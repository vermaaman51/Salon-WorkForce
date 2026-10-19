const {test}=require('node:test');
const assert=require('node:assert/strict');
const {TenantExpiry:{matches,sorted}}=require('../../main/resources/static/js/tenant-expiry.js');
const now=new Date('2026-10-19T10:00:00Z');
const tenant=(id,expiry,active=true)=>({id,tenant_name:id,subscription_expires_at:expiry,_is_active:active});
test('today uses India calendar boundaries and includes earlier expiry today',()=>{
 assert.equal(matches(tenant('start','2026-10-18T18:30:00Z'),'today',now),true);
 assert.equal(matches(tenant('end','2026-10-19T18:29:59Z'),'today',now),true);
 assert.equal(matches(tenant('yesterday','2026-10-18T18:29:59Z'),'today',now),false);
 assert.equal(matches(tenant('tomorrow','2026-10-19T18:30:00Z'),'today',now),false);
});
test('next seven days excludes today and includes all of the seventh calendar day',()=>{
 assert.equal(matches(tenant('today','2026-10-19T17:00:00Z'),'week',now),false);
 assert.equal(matches(tenant('tomorrow','2026-10-19T18:30:00Z'),'week',now),true);
 assert.equal(matches(tenant('seventh','2026-10-26T18:29:59Z'),'week',now),true);
 assert.equal(matches(tenant('eighth','2026-10-26T18:30:00Z'),'week',now),false);
});
test('undated subscriptions never match expiry windows; disabled tenants still count by expiry date',()=>{
 for(const filter of ['today','week'])assert.equal(matches(tenant('none',null),filter,now),false);
 assert.equal(matches(tenant('disabled','2026-10-19T12:00:00Z',false),'today',now),true);
 assert.equal(matches(tenant('disabled','2026-10-19T12:00:00Z',false),'active',now),false);
 assert.equal(matches(tenant('none',null),'active',now),true);
 assert.equal(matches(tenant('expired','2026-10-19T09:00:00Z'),'active',now),false);
 assert.equal(matches(tenant('any',null),null,now),true);
});
test('expiry sorting is chronological, stable by name, with no expiry last; source is preserved',()=>{
 const rows=[tenant('Z',null),tenant('later','2026-10-26T12:00:00Z'),tenant('B','2026-10-19T12:00:00Z'),tenant('A','2026-10-19T12:00:00Z'),tenant('old','2026-10-18T12:00:00Z')];
 assert.deepEqual(sorted(rows).map(t=>t.id),['old','A','B','later','Z']);assert.equal(rows[0].id,'Z');
});
const vm=require('node:vm');
const fs=require('node:fs');
test('dashboard cards start white, select only one filter, and show matching tenants in expiry order',()=>{
 class Element {
  constructor(){this.children=[];this.value='';this.dataset={};this.classes=new Set();this.attributes={};this.classList={toggle:(name,on)=>on?this.classes.add(name):this.classes.delete(name)};}
  append(...nodes){this.children.push(...nodes);} replaceChildren(...nodes){this.children=nodes;}
  setAttribute(name,value){this.attributes[name]=value;} removeAttribute(name){delete this.attributes[name];}
  click(){this.onclick();}
 }
 const nodes=new Map(),get=selector=>{if(!nodes.has(selector))nodes.set(selector,new Element());return nodes.get(selector);};
 const cards=['all','active','today','week'].map(key=>{const node=new Element();node.dataset.tenantFilter=key;return node;});
 const tabs=['tenants','admins'].map(key=>{const node=new Element();node.dataset.view=key;return node;});
 const context=vm.createContext({TenantExpiry:{matches,sorted},Intl,Date,TextEncoder,setInterval:()=>{},fetch:()=>new Promise(()=>{}),document:{hidden:false,querySelector:selector=>selector==='[data-view="tenants"]'?tabs[0]:get(selector),querySelectorAll:selector=>selector==='[data-tenant-filter]'?cards:selector==='[data-view]'?tabs:[],createElement:()=>new Element()}});
 vm.runInContext(fs.readFileSync(require('node:path').join(__dirname,'../../main/resources/static/js/root.js'),'utf8'),context);
 const fixed=new Date();const indiaToday=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata',year:'numeric',month:'2-digit',day:'2-digit'}).format(fixed);
 // An absolute instant at noon India time is inside today's calendar day.
 const today=indiaToday+'T12:00:00+05:30';const next=new Date(new Date(today).getTime()+86400000).toISOString();
 context.fixture=[tenant('tomorrow',next),tenant('undated',null),tenant('today',today)];
 vm.runInContext('tenants=fixture;render();',context);
 assert.equal(cards.some(card=>card.classes.has('selected')),false);
 assert.equal(get('#today-count').textContent,1);assert.equal(get('#week-count').textContent,1);
 assert.deepEqual(get('#tenants-body').children.map(row=>row.children[0].textContent),['today','tomorrow','undated']);
 cards[2].click();assert.equal(cards[2].attributes['aria-pressed'],'true');assert.equal(get('#tenants-body').children.length,1);assert.equal(get('#tenants-body').children[0].children[0].textContent,'today');
 cards[3].click();assert.equal(cards[2].classes.has('selected'),false);assert.equal(cards[3].classes.has('selected'),true);assert.equal(get('#tenants-body').children[0].children[0].textContent,'tomorrow');
 cards[3].click();assert.equal(cards.some(card=>card.classes.has('selected')),false);assert.equal(get('#tenants-body').children.length,3);
});
