(function(scope){
 const plans={BASE:{name:'Base',stores:1,managers:1,receptionists:1,invoices:false,csv:false},PRO:{name:'Pro',stores:3,managers:3,receptionists:3,invoices:true,csv:false},PRO_PLUS:{name:'Pro Plus',stores:7,managers:14,receptionists:14,invoices:true,csv:true}};
 const api={current:plans.BASE,plans,
  set(segment){this.current=plans[segment]||plans.BASE;const badge=document.querySelector('[data-plan-badge]');if(badge){badge.textContent=this.current.name+' Workspace';badge.disabled=false;}document.querySelectorAll('[data-csv-report]').forEach(button=>{button.hidden=!this.current.csv;});},
  async downloadReport(path,fallback='sales-report.csv'){
   const response=await fetch(path,{credentials:'same-origin',cache:'no-store'});
   if(response.status===401){location.href='/login';throw Error('Your session ended. Please sign in again.');}
   if(!response.ok){const error=await response.json().catch(()=>({}));throw Error(error.message||'Unable to download the report.');}
   if(!response.headers.get('Content-Type')?.includes('text/csv'))throw Error('The server did not return a CSV report.');
   const file=await response.blob(),url=URL.createObjectURL(file),link=document.createElement('a');link.href=url;
   link.download=response.headers.get('Content-Disposition')?.match(/filename="([^"]+)"/)?.[1]||fallback;document.body.append(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);
  },
  async invoice(load){try{
   const data=await load();let dialog=document.querySelector('#workspace-invoice');if(!dialog){dialog=document.createElement('dialog');dialog.id='workspace-invoice';dialog.setAttribute('aria-label','Invoice');document.body.append(dialog);}dialog.replaceChildren();
   const add=(parent,tag,text,className)=>{const element=document.createElement(tag);element.textContent=text;if(className)element.className=className;parent.append(element);return element;};
   const paper=document.createElement('article');paper.className='invoice-paper';dialog.append(paper);
   add(paper,'h1','Invoice','invoice-title');
   const business=document.createElement('section');business.className='invoice-business';paper.append(business);
   add(business,'h2',data.tenant_name);add(business,'p',[data.store_name,data.store_address,data.city].filter(Boolean).join(' · '));
   if(data.gst_number)add(business,'p','GSTIN: '+data.gst_number);
   add(business,'p','Invoice no: '+data.invoice_no);add(business,'p','Appointment: '+new Date(data.appointment_at).toLocaleString());
   const customer=document.createElement('section');customer.className='invoice-customer';paper.append(customer);add(customer,'h3','Customer details');add(customer,'p',data.customer_name,'invoice-customer-name');add(customer,'p','Mobile: '+data.customer_mobile);
   const table=document.createElement('table');table.className='invoice-items';const head=document.createElement('thead'),header=document.createElement('tr');add(header,'th','Service');add(header,'th','Amount');head.append(header);table.append(head);const body=document.createElement('tbody'),row=document.createElement('tr');add(row,'td',data.service||'Appointment');add(row,'td',Number(data.amount).toFixed(2));body.append(row);table.append(body);paper.append(table);
   const total=document.createElement('div');total.className='invoice-total';add(total,'span','Total amount');add(total,'strong',Number(data.amount).toFixed(2));paper.append(total);
   const payment=document.createElement('section');payment.className='invoice-payment';paper.append(payment);add(payment,'p','Payment status: '+(data.payment_status==='COMPLETED'?'Paid':'Pending'));add(payment,'p','Payment mode: '+(data.payment_mode==='CASH'?'Cash':data.payment_mode||'Not recorded'));
   add(paper,'p','Thank you for visiting.','invoice-thanks');
   const actions=document.createElement('div');actions.className='invoice-actions';for(const [name,fn] of [['Close',()=>dialog.close()],['Print invoice',()=>window.print()]]){const button=document.createElement('button');button.type='button';button.textContent=name;button.className=name==='Close'?'secondary':'primary';button.onclick=fn;actions.append(button);}dialog.append(actions);dialog.showModal();
  }catch(error){const notice=document.querySelector('#notice');notice.textContent=error.message;notice.hidden=false;notice.classList.add('error');}}
 };
 scope.WorkspacePlan=api;
 if(typeof document==='undefined')return;
 const badge=document.querySelector('[data-plan-badge]');if(badge)badge.onclick=()=>{let dialog=document.querySelector('#workspace-plans');if(!dialog){dialog=document.createElement('dialog');dialog.id='workspace-plans';dialog.setAttribute('aria-label','Workspace plans and benefits');document.body.append(dialog);}dialog.replaceChildren();const title=document.createElement('h2');title.textContent='Choose the plan for your business';dialog.append(title);const grid=document.createElement('div');grid.className='workspace-plan-grid';for(const plan of Object.values(plans)){const card=document.createElement('article'),heading=document.createElement('h3');heading.textContent=plan.name+(plan===api.current?' · Current plan':'');if(plan===api.current){card.classList.add('current-plan');card.setAttribute('aria-current','true');}card.append(heading);const list=document.createElement('ul');for(const text of [`${plan.stores} store${plan.stores===1?'':'s'}`,`${plan.managers} store manager${plan.managers===1?'':'s'}`,`${plan.receptionists} receptionist${plan.receptionists===1?'':'s'}`,plan.invoices?'Printable invoices · Store Manager & Receptionist':'Appointment and payment management',...(plan.csv?['CSV sales reports · Admin & Store Manager']:[])]){const li=document.createElement('li');li.textContent=text;list.append(li);}card.append(list);grid.append(card);}dialog.append(grid);const note=document.createElement('p');note.textContent='Limits apply across your tenant. Contact the platform owner to change your plan.';const close=document.createElement('button');close.type='button';close.className='primary';close.textContent='Close';close.onclick=()=>dialog.close();dialog.append(note,close);dialog.showModal();};
})(typeof module!=='undefined'?module.exports:window);
