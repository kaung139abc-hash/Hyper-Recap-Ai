const cors = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Methods": "GET,POST,OPTIONS", "Access-Control-Allow-Headers": "Content-Type,Authorization" };
const json = (data, status=200) => new Response(JSON.stringify(data), { status, headers: { ...cors, "Content-Type":"application/json" } });
const hash = async s => [...new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)))].map(x=>x.toString(16).padStart(2,"0")).join("");
const key = () => "HRAI-" + crypto.randomUUID().replaceAll("-","").slice(0,20).toUpperCase();
async function body(req){ try{return await req.json()}catch{return {}} }
function admin(req, env){ return req.headers.get("Authorization") === `Bearer ${env.ADMIN_SECRET}` && !!env.ADMIN_SECRET; }
export default {
 async fetch(req, env) {
  if(req.method==="OPTIONS") return new Response(null,{headers:cors});
  const url=new URL(req.url), path=url.pathname;
  const declaredLength=Number(req.headers.get("Content-Length")||0);
  if(declaredLength>150_000) return json({error:"Request body too large"},413);
  if(path==="/health") return json({ok:true,service:"Hyper Recap AI License Server",aiConfigured:!!env.AI,databaseConfigured:!!env.DB,adminConfigured:!!env.ADMIN_SECRET});
  if(req.method==="GET" && (path==="/admin" || path==="/admin.html")) {
   return env.ASSETS.fetch(new Request(new URL("/admin.html",url),req));
  }
  if(req.method!=="POST") return json({error:"POST required"},405);
  const b=await body(req);
  if(path==="/v1/activate" || path==="/v1/check"){
   if(typeof b.licenseKey!=="string" || b.licenseKey.trim().length<8 || b.licenseKey.length>128 || typeof b.deviceId!=="string" || !b.deviceId.trim() || b.deviceId.length>160) return json({error:"valid licenseKey and deviceId required"},400);
   const h=await hash(b.licenseKey.trim().toUpperCase());
   const row=await env.DB.prepare("SELECT * FROM licenses WHERE key_hash=?").bind(h).first();
   if(!row || row.status!=="active") return json({valid:false,reason:"INVALID_OR_REVOKED"});
   if(row.expires_at && (!Number.isFinite(Date.parse(row.expires_at)) || Date.parse(row.expires_at)<=Date.now())) return json({valid:false,reason:"EXPIRED"});
   let devices=JSON.parse(row.devices_json||"[]");
   if(!Array.isArray(devices)) devices=[];
   if(!devices.includes(b.deviceId)){
    if(path==="/v1/check") return json({valid:false,reason:"DEVICE_NOT_ACTIVATED"});
    if(devices.length>=row.device_limit) return json({valid:false,reason:"DEVICE_LIMIT"});
    devices.push(b.deviceId);
    await env.DB.prepare("UPDATE licenses SET devices_json=? WHERE id=?").bind(JSON.stringify(devices),row.id).run();
   }
   return json({valid:true,plan:row.plan,expiresAt:row.expires_at,deviceLimit:row.device_limit});
  }
  if(path==="/v1/ai/recap" || path==="/v1/ai/translate"){
   if(!env.AI) return json({error:"AI binding is not configured"},503);
   if(typeof b.licenseKey!=="string" || typeof b.deviceId!=="string" || typeof b.text!=="string") return json({error:"licenseKey, deviceId and text are required"},400);
   const text=b.text.trim();
   const maxChars=path.endsWith("/recap")?24000:6000;
   if(text.length<30 || text.length>maxChars) return json({error:"Text length must be between 30 and "+maxChars+" characters"},400);
   const digest=await hash(b.licenseKey.trim().toUpperCase());
   const license=await env.DB.prepare("SELECT * FROM licenses WHERE key_hash=?").bind(digest).first();
   if(!license || license.status!=="active") return json({error:"INVALID_OR_REVOKED"},403);
   if(license.expires_at){ const expiry=Date.parse(license.expires_at); if(!Number.isFinite(expiry) || expiry<=Date.now()) return json({error:"EXPIRED"},403); }
   let devices=[]; try{devices=JSON.parse(license.devices_json||"[]")}catch{}
   if(!Array.isArray(devices) || !devices.includes(b.deviceId)) return json({error:"DEVICE_NOT_ACTIVATED"},403);
   try{
    if(path.endsWith("/recap")){
     const result=await env.AI.run("@cf/meta/llama-3.1-8b-instruct",{
      messages:[
       {role:"system",content:"You are a careful movie recap writer. Write a coherent, chronological, engaging recap in Burmese. Use only facts stated in the transcript. Do not invent characters, events, motivations, or an ending. Preserve names. If the transcript is incomplete, say so. Return only the recap."},
       {role:"user",content:"Create a detailed but concise movie recap from this transcript:\n\n"+text}
      ],
      max_tokens:1200,
      temperature:0.35
     });
     const output=result?.response||result?.result?.response||"";
     if(!output) return json({error:"AI returned an empty recap"},502);
     return json({ok:true,recap:output.trim(),model:"llama-3.1-8b-instruct"});
    }
    const result=await env.AI.run("@cf/meta/m2m100-1.2b",{text,source_lang:"en",target_lang:"my"});
    const output=result?.translation||result?.translated_text||"";
    if(!output) return json({error:"AI returned an empty translation"},502);
    return json({ok:true,translation:output,model:"m2m100-1.2b"});
   }catch(error){
    return json({error:"AI inference failed: "+String(error?.message||error).slice(0,240)},502);
   }
  }
  if(path.startsWith("/admin/")){
   if(!admin(req,env)) return json({error:"Unauthorized"},401);
   if(path==="/admin/create"){
    if(!["trial","monthly","lifetime"].includes(b.plan)) return json({error:"Invalid plan"},400);
    const plan=b.plan;
    const days=plan==="trial"?3:plan==="monthly"?30:0;
    const license=key(), expiry=days?new Date(Date.now()+days*86400000).toISOString():null;
    await env.DB.prepare("INSERT INTO licenses (key_hash,plan,status,expires_at,device_limit,devices_json,customer_note,created_at) VALUES (?,?,?,?,?,?,?,?)").bind(await hash(license),plan,"active",expiry,Math.max(1,Math.min(5,Number(b.deviceLimit)||1)),"[]",String(b.customerNote||"").slice(0,200),new Date().toISOString()).run();
    return json({licenseKey:license,plan,expiresAt:expiry});
   }
   if(path==="/admin/list"){
    const {results}=await env.DB.prepare("SELECT id,plan,status,expires_at,device_limit,devices_json,customer_note,created_at FROM licenses ORDER BY id DESC LIMIT 500").all();
    return json({licenses:results.map(x=>({...x,devices:JSON.parse(x.devices_json||"[]")}))});
   }
   if(path==="/admin/revoke"){
    const id=Number(b.id); if(!Number.isSafeInteger(id)||id<1) return json({error:"Invalid license id"},400);
    const result=await env.DB.prepare("UPDATE licenses SET status='revoked' WHERE id=?").bind(id).run(); return json({ok:true,changes:result.meta?.changes||0});
   }
   if(path==="/admin/extend"){
    const id=Number(b.id), days=Number(b.days); if(!Number.isSafeInteger(id)||id<1||!Number.isFinite(days)) return json({error:"Invalid id or days"},400);
    const row=await env.DB.prepare("SELECT * FROM licenses WHERE id=?").bind(id).first();
    if(!row)return json({error:"Not found"},404);
    const base=row.expires_at&&Date.parse(row.expires_at)>Date.now()?Date.parse(row.expires_at):Date.now();
    const expiry=new Date(base+Math.max(1,Math.min(3650,Number(b.days)||30))*86400000).toISOString();
    await env.DB.prepare("UPDATE licenses SET expires_at=?,status='active' WHERE id=?").bind(expiry,row.id).run(); return json({ok:true,expiresAt:expiry});
   }
   if(path==="/admin/stats"){
    const row=await env.DB.prepare("SELECT COUNT(*) total, SUM(CASE WHEN status='active' THEN 1 ELSE 0 END) active FROM licenses").first();
    return json(row);
   }
  }
  return json({error:"Not found"},404);
 }
};