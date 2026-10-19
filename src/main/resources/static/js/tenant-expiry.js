/* Calendar-day subscription filters use the business timezone, independent of browser timezone. */
(function(scope){
 const dayFormat=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata',year:'numeric',month:'2-digit',day:'2-digit'});
 function dayNumber(date){const parts=Object.fromEntries(dayFormat.formatToParts(date).map(p=>[p.type,p.value]));return Date.UTC(+parts.year,+parts.month-1,+parts.day)/86400000;}
 function expiry(tenant){const value=tenant.subscription_expires_at;if(!value)return Infinity;const time=new Date(value).getTime();return Number.isFinite(time)?time:Infinity;}
 function matches(tenant,filter,now=new Date()){
  if(!filter||filter==='all')return true;
  const timestamp=expiry(tenant);
  if(filter==='active')return tenant._is_active&&timestamp>now.getTime();
  if(!Number.isFinite(timestamp))return false;
  const days=dayNumber(new Date(timestamp))-dayNumber(now);
  if(filter==='today')return days===0;
  if(filter==='week')return days>=1&&days<=7;
  return false;
 }
 function sorted(tenants){return [...tenants].sort((a,b)=>{const left=expiry(a),right=expiry(b);return (left===right?0:left<right?-1:1)||String(a.tenant_name||'').localeCompare(String(b.tenant_name||''))||String(a.id).localeCompare(String(b.id));});}
 scope.TenantExpiry={matches,sorted};
})(typeof module!=='undefined'?module.exports:window);
