const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const source=name=>fs.readFileSync(path.join(__dirname,'../../main/resources/static/',name),'utf8');

function workspace(script,{hash='',manager=true,pathname='/manager/dashboard'}={}){
 const nodes=new Map(),events={},requests=[];let releaseInitial;
 function node(){return {hidden:true,value:'',dataset:{},attributes:{},children:[],options:[],classList:{toggle(){}},setAttribute(key,value){this.attributes[key]=value;},removeAttribute(key){delete this.attributes[key];},append(...items){this.children.push(...items);this.options.push(...items);},replaceChildren(...items){this.children=items;this.options=items;},querySelector(){return null;}};}
 function find(key){if(!nodes.has(key))nodes.set(key,node());return nodes.get(key);}
 const views=manager?['dashboard','receptionists','experts','appointments']:['dashboard','queries','appointments'];
 const nav=views.map(view=>{const button=node();button.dataset.view=view;return button;});
 const context=vm.createContext({
  document:{body:{dataset:{workflow:manager?'manager':''},classList:{toggle(){}}},querySelector:key=>key==='#appointment-dashboard'&&script==='reception.js'?null:find(key),querySelectorAll:selector=>selector.includes('[data-view]')?nav:[],createElement:node},
  location:{hash,href:'',pathname},window:{addEventListener:(name,handler)=>{events[name]=handler;}},
  fetch:url=>{requests.push(url);return new Promise(resolve=>{releaseInitial=resolve;});},
  AppointmentDashboard:class{render(){}resetFilters(){}},OrderPager(){},WorkspacePlan:{current:{},set(){}},URLSearchParams,Date,Number,FormData:class{},
 });
 vm.runInContext(source('js/reception.js'),context);
 if(manager)vm.runInContext(source('js/manager.js'),context);
 return {context,find,nav,requests,events,releaseInitial:response=>releaseInitial(response)};
}

test('Manager appointments selects the requested view before any session response',()=>{
 for(const [hash,expected] of [['','appointments'],['#checkout','appointments']]){
  const env=workspace('reception.js',{hash,pathname:'/manager/appointments'});
  assert.equal(env.find('#'+expected+'-view').hidden,false);
  assert.equal(env.find('#dashboard-view').hidden,true);
  assert.equal(env.find('#page-title').textContent,'Customer appointments');
  assert.equal(env.requests.length,1);
  assert.equal(env.requests[0],'/auth/csrf');
 }
 const html=source('manager.html');
 assert.ok(html.includes('<section id="dashboard-view" hidden>'));
 assert.ok(!html.includes('Every customer,'));
 assert.ok(html.includes('<section id="appointments-view" hidden>'));
});

test('Manager staff destinations select immediately and separate checkout navigation is removed',()=>{
 for(const view of ['dashboard','receptionists','experts']){
  const env=workspace('manager.js',{hash:view==='dashboard'?'':'#'+view});
  assert.equal(env.find('#'+view+'-view').hidden,false);
  assert.equal(env.find('#dashboard-view').hidden,view!=='dashboard');
  env.nav.find(button=>button.dataset.view==='appointments').onclick();
  assert.equal(env.find('#appointments-view').hidden,false);
  assert.equal(env.context.location.href,'');
 }
 const html=source('manager.html');
 assert.ok(!html.includes('id="open-checkout"'));
 assert.ok(html.includes('<section id="dashboard-view" hidden>'));
});

test('Receptionist appointment navigation loads the list with a single customer appointment heading',async()=>{
 const env=workspace('reception.js',{manager:false});let loads=0;
 env.context.trackLoad=()=>{loads++;};
 vm.runInContext('session={};render=()=>{};refresh=async()=>trackLoad();',env.context);
 const appointments=env.nav.find(button=>button.dataset.view==='appointments');appointments.onclick();
 assert.equal(env.find('#appointments-view').hidden,false);
 assert.equal(env.find('#dashboard-view').hidden,true);
 assert.equal(appointments.attributes['aria-current'],'page');
 assert.equal(env.find('#page-title').textContent,'Customer appointments');
 assert.equal(loads,1);
 env.nav.find(button=>button.dataset.view==='dashboard').onclick();
 assert.equal(env.find('#dashboard-view').hidden,false);
 assert.equal(loads,2);
});

test('Both appointment pages remove the duplicate heading and checkout section but retain card payment actions',()=>{
 for(const file of ['manager.html','reception.html']){
  const html=source(file);
  assert.ok(!html.includes('<h2>Customer appointments</h2>'));
  assert.ok(!html.includes('data-view="checkout"'));
  assert.ok(!html.includes('id="checkout-view"'));
 }
 const script=source('js/reception.js');
 assert.ok(script.includes("button(actions,'Customer checkout'"));
 assert.ok(script.includes("button(actions,'Mark paid'"));
});

test('Receptionist still starts on its dashboard before session loading',()=>{
 const env=workspace('reception.js',{manager:false});
 assert.equal(env.find('#dashboard-view').hidden,false);
 assert.equal(env.find('#appointments-view').hidden,true);
 assert.equal(env.find('#page-title').textContent,'Dashboard');
});

// Exercise both scripts together: the sidebar and selected store belong to the
// shared controller, while team and appointment editors remain independent.
test('Repeated Manager sidebar clicks stay in one document and retain the selected store',()=>{
 const env=workspace('reception.js');let loads=0;
 env.context.trackLoad=()=>{loads++;};
 vm.runInContext("session={};storeId='selected-store';render=()=>{};refresh=async()=>trackLoad();",env.context);
 for(const view of ['appointments','dashboard','receptionists','appointments','experts','dashboard']){
  env.nav.find(button=>button.dataset.view===view).onclick();
  assert.equal(env.find('#'+view+'-view').hidden,false);
  assert.equal(env.context.location.href,'');
  assert.equal(vm.runInContext('storeId',env.context),'selected-store');
  assert.equal(env.nav.filter(button=>button.attributes['aria-current']==='page').length,1);
 }
 assert.equal(loads,6);
});

test('Unified Manager markup has one sidebar and distinct team and appointment editors',()=>{
 const html=source('manager.html');
 const ids=[...html.matchAll(/\bid="([^"]+)"/g)].map(match=>match[1]);
 assert.equal(new Set(ids).size,ids.length,'Duplicate element IDs');
 assert.equal((html.match(/<aside>/g)||[]).length,1);
 assert.ok(html.includes('data-view="appointments"'));
 assert.ok(html.includes('id="staff-editor-form"'));
 assert.ok(html.includes('id="editor-form"'));
 assert.ok(!source('js/manager.js').includes("location.href='/manager/appointments'"));
});

test('Shared workflow still uses Manager-scoped endpoints and the selected store',async()=>{
 const env=workspace('reception.js'),calls=[];
 env.context.fetch=async(url,options)=>{calls.push({url,options});return {ok:true,status:200,text:async()=>JSON.stringify([])};};
 vm.runInContext("storeId='store-a';csrf={header:'X-CSRF',token:'token'};",env.context);
 await vm.runInContext("api('/orders?view=total')",env.context);
 await vm.runInContext("api('/experts','GET',undefined,'/api/manager')",env.context);
 assert.equal(calls[0].url,'/api/manager/appointments/orders?view=total&storeId=store-a');
 assert.equal(calls[1].url,'/api/manager/experts?storeId=store-a');
 assert.equal(calls[0].options.headers['X-CSRF'],'token');
});

test('Manager initialization loads appointment data in the unified page without replacing the sidebar',async()=>{
 const env=workspace('reception.js',{pathname:'/manager/appointments'}),calls=[];
 env.context.fetch=async(url)=>{calls.push(url);const data=url.endsWith('/session')?{name:'Store Manager',stores:[{id:'store-a',name:'Store A',tenant_name:'Tenant',segment_type:'BASE'}]}:url.includes('/experts?')?[]:{items:[],page:0,total:0,totalPages:0};return {ok:true,status:200,text:async()=>JSON.stringify(data)};};
 env.releaseInitial({ok:true,json:async()=>({header:'X-CSRF',token:'token'})});
 await new Promise(resolve=>setImmediate(resolve));
 assert.equal(env.find('#notice').hidden,true,env.find('#notice').textContent);
 assert.equal(env.find('#page-title').textContent,'Customer appointments');
 assert.equal(env.find('#appointments-view').hidden,false);
 assert.ok(calls.includes('/api/manager/appointments/session'));
 assert.ok(calls.some(url=>url.startsWith('/api/manager/appointments/orders?')&&url.includes('storeId=store-a')));
 assert.equal(env.find('#tenant-name').textContent,'Store A \u00b7 Tenant');
 assert.equal(env.context.location.href,'');
});
