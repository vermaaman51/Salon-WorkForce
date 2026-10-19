(function(scope){
 function summarize(orders,filters={},tab='total'){
  const all=orders.filter(o=>!o.order_type||o.order_type==='APPOINTMENT');
  const filtered=all.filter(o=>(!filters.expert||(filters.expert==='UNASSIGNED'?!o.assignee:o.assignee===filters.expert))&&(!filters.status||o.order_status===filters.status)&&(!filters.payment||o.payment_status===filters.payment));
  const unassigned=filtered.filter(o=>!o.assignee);
  const running=filtered.filter(o=>o.order_status==='PENDING'||o.order_status==='ASSIGNED');
  const completed=filtered.filter(o=>o.order_status==='COMPLETED');
  const paid=completed.filter(o=>o.payment_status==='COMPLETED');
  return {total:filtered.length,unassigned:unassigned.length,running:running.length,completed:completed.length,sales:paid.reduce((sum,o)=>sum+Math.round(Number(o.amount)*100),0)/100,rows:tab==='unassigned'?unassigned:tab==='running'?running:tab==='completed'?completed:tab==='sales'?paid:filtered};
 }
 function pager(mount,result,load){mount.replaceChildren();const previous=document.createElement('button'),next=document.createElement('button'),label=document.createElement('span');previous.type=next.type='button';previous.className=next.className='secondary';previous.textContent='Previous';next.textContent='Next';previous.disabled=result.page<=0;next.disabled=result.page+1>=result.totalPages;previous.onclick=()=>load(result.page-1);next.onclick=()=>load(result.page+1);label.textContent=result.total&&!(result.items||result.orders||[]).length?'No records on this page · '+result.total+' total':result.total?`${result.page*20+1}–${Math.min((result.page+1)*20,result.total)} of ${result.total} · 20 per page`:'0 results';mount.className='order-pagination';mount.append(previous,label,next);}
 class AppointmentDashboard{
  constructor(mount,options){this.mount=mount;this.options=options;this.tab='total';this.page=0;this.result=null;this.rows=[];this.busy=false;this.sequence=0;   mount.innerHTML=`<div class="stats appointment-summary" role="tablist" aria-label="Appointment activity">${[['total','Total appointments'],['unassigned','Unassigned appointments'],['running','Running appointments'],['completed','Completed appointments'],['sales','Total sales']].map(([key,label])=>`<button type="button" class="activity-card" id="${key}-tab" role="tab" aria-controls="activity-panel" aria-selected="${key==='total'}" tabindex="${key==='total'?0:-1}" data-activity="${key}"><span>${label}</span><strong data-total="${key}">0</strong></button>`).join('')}</div><div class="section-heading"><div><h2>Appointment activity</h2><p id="activity-summary" aria-live="polite"></p></div><div class="report-actions">${options.csv?'<button class="secondary" id="download-sales" type="button" data-csv-report hidden disabled>Download CSV</button>':''}<button class="secondary" id="refresh-dashboard" type="button">Refresh</button></div></div><div class="activity-controls appointment-filters"><label>Expert<select id="dashboard-expert-filter"><option value="">All experts</option><option value="UNASSIGNED">Unassigned</option></select></label><label>Order status<select id="dashboard-status-filter"><option value="">All statuses</option><option value="PENDING">Pending</option><option value="ASSIGNED">Assigned</option><option value="COMPLETED">Completed</option></select></label><label>Payment status<select id="dashboard-payment-filter"><option value="">All payments</option><option value="PENDING">Pending</option><option value="COMPLETED">Completed (paid)</option></select></label></div><div id="activity-panel" role="tabpanel" aria-labelledby="total-tab" tabindex="0"><div class="table-card"><table><thead><tr><th>Customer name</th><th>Service name</th><th>Amount</th><th>Order status</th><th>Payment status</th><th>Expert</th></tr></thead><tbody id="activity-body"></tbody></table><div class="empty" id="activity-empty" hidden><h3>No appointments to show.</h3><p id="activity-empty-text"></p></div></div></div>`;
   if(options.dateRange){
    const form=document.createElement('form');form.className='sales-filters';
    form.innerHTML='<label>From<input id="dashboard-from" type="date" required></label><label>To<input id="dashboard-to" type="date" required></label><button type="submit" class="primary" aria-label="Search appointments"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="11" cy="11" r="7"></circle><path d="m16 16 4 4"></path></svg> Search</button>';
    const parts=new Intl.DateTimeFormat('en-US',{timeZone:'Asia/Kolkata',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date()),part=key=>parts.find(p=>p.type===key).value;
    const today=part('year')+'-'+part('month')+'-'+part('day');
    for(const input of form.querySelectorAll('input')){input.value=today;input.max=today;}
    this.range={from:today,to:today};
    form.onsubmit=e=>{e.preventDefault();const from=this.find('#dashboard-from').value,to=this.find('#dashboard-to').value;if(from>to||(new Date(to)-new Date(from))/86400000>=30){options.error(Error('Choose a range of up to 30 days, with From before To.'));return;}this.range={from,to};this.reload(0);};
    mount.insertBefore(form,this.find('.appointment-filters'));
   }
   const pages=document.createElement('div');pages.id='dashboard-pages';mount.append(pages);
   this.cards=[...mount.querySelectorAll('[data-activity]')];
   for(const card of this.cards){card.onclick=()=>this.select(card.dataset.activity);card.onkeydown=e=>{let i=this.cards.indexOf(card);if(e.key==='ArrowRight')i=(i+1)%this.cards.length;else if(e.key==='ArrowLeft')i=(i+this.cards.length-1)%this.cards.length;else if(e.key==='Home')i=0;else if(e.key==='End')i=this.cards.length-1;else return;e.preventDefault();this.cards[i].focus();this.select(this.cards[i].dataset.activity);};}
   for(const selector of ['#dashboard-expert-filter','#dashboard-status-filter','#dashboard-payment-filter'])this.find(selector).onchange=()=>this.reload(0);
   this.find('#refresh-dashboard').onclick=async()=>{try{await options.refresh();}catch(error){options.error(error);}};
   if(options.csv){const button=this.find('#download-sales');button.textContent="Download today's CSV";button.onclick=async()=>{button.disabled=true;try{await options.export();}catch(error){options.error(error);}finally{button.disabled=this.busy||!this.options.hasStore();}};}

  }
  find(selector){return this.mount.querySelector(selector);}
  select(tab){this.tab=tab;return this.reload(0);}
  resetFilters(){this.sequence++;this.result=null;this.page=0;for(const selector of ['#dashboard-expert-filter','#dashboard-status-filter','#dashboard-payment-filter'])this.find(selector).value='';}
  setBusy(busy){this.busy=busy;this.find('#refresh-dashboard').disabled=busy||!this.options.hasStore();if(this.options.csv)this.find('#download-sales').disabled=busy||!this.options.hasStore();for(const control of this.mount.querySelectorAll('select,[data-activity],#dashboard-pages button'))control.disabled=busy||!this.options.hasStore();if(!busy&&this.result)pager(this.find('#dashboard-pages'),this.result,page=>this.reload(page));}
  async reload(page=this.page){const sequence=++this.sequence;this.setBusy(true);try{if(!this.options.hasStore()){this.result=null;this.render();return;}const result=await this.options.load({...this.range,page,view:this.tab,type:'APPOINTMENT',expert:this.find('#dashboard-expert-filter').value,status:this.find('#dashboard-status-filter').value,payment:this.find('#dashboard-payment-filter').value});if(sequence!==this.sequence)return;this.result=result;this.page=result.page;this.render();}catch(error){if(sequence===this.sequence){this.result=null;this.render();this.options.error(error);}}finally{if(sequence===this.sequence)this.setBusy(false);}}
  render(){
   const orders=this.result?.items||[],names=new Map(this.options.experts().map(e=>[e.id,e.name]));for(const order of orders)if(order.assignee&&!names.has(order.assignee))names.set(order.assignee,'Assigned expert');
   const select=this.find('#dashboard-expert-filter'),selected=select.value;if(selected&&selected!=='UNASSIGNED'&&!names.has(selected))names.set(selected,'Selected expert');select.replaceChildren(new Option('All experts',''),new Option('Unassigned','UNASSIGNED'));for(const [id,name] of names)select.add(new Option(name,id));select.value=selected||'';
   const summary=this.result?.summary||{total:0,unassigned:0,running:0,completed:0,sales:0};
   const money=value=>Number(value).toLocaleString(undefined,{minimumFractionDigits:2,maximumFractionDigits:2}),status=value=>({PENDING:'Pending',ASSIGNED:'Assigned',COMPLETED:'Completed'})[value]||value;
   for(const key of ['total','unassigned','running','completed','sales'])this.find('[data-total="'+key+'"]').textContent=key==='sales'?money(summary[key]):summary[key];
   for(const card of this.cards){const active=card.dataset.activity===this.tab;card.setAttribute('aria-selected',String(active));card.tabIndex=active?0:-1;}this.find('#activity-panel').setAttribute('aria-labelledby',this.tab+'-tab');
   this.rows=orders.map(o=>[o.customer_name,o.service_name??o.service,Number(o.amount).toFixed(2),status(o.order_status),o.payment_status==='COMPLETED'?'Paid':'Pending',o.assignee?(names.get(o.assignee)||'Assigned expert'):'Not assigned']);
   const body=this.find('#activity-body');body.replaceChildren();for(const row of this.rows){const tr=document.createElement('tr');for(const value of row){const td=document.createElement('td');td.textContent=value??'';tr.append(td);}body.append(tr);}
   this.find('#activity-summary').textContent=(this.result?.total||0)+' matching '+({total:'appointments',unassigned:'unassigned appointments',running:'running appointments',completed:'completed appointments',sales:'paid, completed appointments'})[this.tab]+' · latest modified first';
   this.find('#activity-empty').hidden=this.rows.length>0;this.find('#activity-empty-text').textContent=this.options.hasStore()?'No appointments match these filters.':'No store is assigned to this account.';
   if(!this.result)this.find('#dashboard-pages').replaceChildren();this.setBusy(this.busy);
  }
 }
 scope.AppointmentDashboard=AppointmentDashboard;scope.AppointmentSummary=summarize;scope.OrderPager=pager;
})(typeof module!=='undefined'?module.exports:window);
