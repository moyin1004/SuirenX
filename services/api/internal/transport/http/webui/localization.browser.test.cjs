const {test}=require('node:test');
let chromium;try{({chromium}=require('playwright'))}catch(_){}
const http=require('http'),fs=require('fs'),assert=require('node:assert/strict');
const base=__dirname;
const server=http.createServer((req,res)=>{let file=req.url==='/'?'index.html':req.url.replace('/web/','');try{let body=fs.readFileSync(base+'/'+file);if(file==='index.html')body=body.toString().replace('{{.Version}}','test-build');res.setHeader('Content-Type',file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':'text/html');res.end(body)}catch{res.statusCode=404;res.end()}});
test('language switches preserve all page, form, conflict and secret state without API requests',{skip:!chromium?'Install Playwright to run browser acceptance':false},async t=>{
 t.after(()=>server.close());
 await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(0,'127.0.0.1',resolve)});
 const browser=await chromium.launch({headless:true,...(process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE?{executablePath:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE}:{})});
 t.after(()=>browser.close());
 const context=await browser.newContext({locale:'en-US',viewport:{width:1440,height:1000}}),page=await context.newPage();
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 let requests=[];
 const asset={id:'a1',name:'我的电脑',purchase_channel:'测试',tags:['标签'],price_cents:120000,status:'ACTIVE',purchase_date:'2026-10-01',held_days:1,daily_cost_cents:120000,version:1};
 const item={id:'x1',name:'洗面奶',status:'IN_USE',category:'洗护',location:'',package_expiry_date:'2026-10-21',version:1};
 const configs=[{config_key:'c1',display_name:'原文配置',content:'port: 7890\n名字: 保留\n',updated_at:'2026-10-20T12:00:00Z'}];
 const tokens=[{id:'t1',label:'私人 Token',transport_mode:'BEARER',config_keys:['c1'],created_at:'2026-10-20T12:00:00Z'}];
 await page.route('**/api/**',async route=>{const req=route.request(),url=new URL(req.url());requests.push({path:url.pathname,method:req.method(),body:req.postData()});let response;
 if(url.pathname.includes('/auth/login'))response={access_token:'test-token'};
 else if(url.pathname==='/api/v1/admin/accounts')response={accounts:[{id:'u1',username:'其他用户',role:'USER',created_at:'2026-10-20T12:00:00Z'}]};
 else if(url.pathname.endsWith('/assets'))response={assets:[asset]};
 else if(url.pathname.endsWith('/assets/a1'))response=asset;
 else if(url.pathname.endsWith('/expiry'))response={items:[item]};
 else if(url.pathname==='/api/v1/admin/configs')response={configs};
 else if(url.pathname==='/api/v1/admin/configs/c1')response=configs[0];
 else if(url.pathname==='/api/v1/admin/settings/account-registration')response={enabled:true};
 else if(url.pathname==='/api/v1/admin/config-tokens')response={tokens};
 else if(url.pathname==='/api/v1/admin/audit-log')response={entries:[{actor_username:'admin',actor_id:'ad1',action:'CONFIG_CREATED',target_type:'config',target_id:'c1',created_at:'2026-10-20T12:00:00Z'}],retention_limit:1000};
 else throw Error('Unexpected request '+url.pathname);
 await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(response)});
 });
 const url='http://127.0.0.1:'+server.address().port;
 const switchTo=async language=>{const before=requests.length;await page.locator('.modal [data-change="language"],.login-panel [data-change="language"],.top-actions [data-change="language"]').last().selectOption(language);assert.equal(requests.length,before,'language change must not request API');assert.equal(await page.locator('html').getAttribute('lang'),language||'en')};
 await page.goto(url);await page.locator('#login-form').waitFor();
 assert.equal(await page.title(),'SuirenX · Asset workspace');
 await page.locator('[name="username"]').fill('test-admin');await page.locator('[name="password"]').fill('private-password');await page.locator('[data-login-mode="admin"]').click();
 await switchTo('zh-Hans');assert.equal(await page.locator('[name="password"]').inputValue(),'private-password');assert.equal(await page.locator('[data-login-mode="admin"]').getAttribute('class'),'active');
 await switchTo('en');await page.locator('#login-form button[type="submit"]').click();await page.locator('.shell').waitFor();
 for(const name of ['home','assets','expiry','conflicts','accounts','configs','tokens','audit']){
  await page.locator('[data-page="'+name+'"]').first().click();await page.locator('.shell').waitFor();
  await switchTo('zh-Hans');await switchTo('en');
  const text=await page.locator('.main').innerText();assert.ok(text.trim().length>20,name);
  if(name==='assets'){
   await page.locator('[data-change="asset-status"]').selectOption('ACTIVE');await page.locator('[data-input="query"]').fill('我的');
   await page.locator('[data-action="edit-asset"]').click();await page.locator('#asset-form').waitFor();
   await page.locator('[name="name"]').fill('尚未保存的新名称');await page.locator('[name="notes"]').fill('保持备注 <script>');await page.locator('[name="price"]').fill('777.77');await page.locator('[name="status"]').selectOption('RETIRED');
   await switchTo('zh-Hans');await switchTo('en');
   assert.equal(await page.locator('[name="name"]').inputValue(),'尚未保存的新名称');assert.equal(await page.locator('[name="notes"]').inputValue(),'保持备注 <script>');assert.equal(await page.locator('[name="price"]').inputValue(),'777.77');assert.equal(await page.locator('[name="status"]').inputValue(),'RETIRED');assert.equal(await page.locator('[data-input="query"]').inputValue(),'我的');assert.equal(await page.locator('[data-change="asset-status"]').inputValue(),'ACTIVE');
   await page.locator('[data-action="close-modal"]').first().click();
  }
  if(name==='expiry'){
   await page.locator('[data-action="edit-expiry"]').click();await page.locator('[name="location"]').fill('  未保存位置  ');await page.locator('[name="package_expiry_date"]').fill('2027-01-01');await switchTo('zh-Hans');assert.equal(await page.locator('[name="location"]').inputValue(),'  未保存位置  ');assert.equal(await page.locator('[name="package_expiry_date"]').inputValue(),'2027-01-01');await page.locator('[data-action="close-modal"]').first().click();await switchTo('en');
  }
  if(name==='configs'){
   await page.locator('[data-action="edit-config"]').click();await page.locator('[name="content"]').fill('port: 1234\n新内容: 保持原文\n\n');await switchTo('zh-Hans');await switchTo('en');assert.equal(await page.locator('[name="content"]').inputValue(),'port: 1234\n新内容: 保持原文\n\n');await page.locator('[data-action="close-modal"]').first().click();
  }
  if(name==='tokens'){
   await page.locator('[data-action="edit-token"]').click();await page.locator('[name="label"]').fill('未保存 token');await page.locator('[name="config_keys"]').uncheck();await switchTo('zh-Hans');await switchTo('en');assert.equal(await page.locator('[name="label"]').inputValue(),'未保存 token');assert.equal(await page.locator('[name="config_keys"]').isChecked(),false);await page.locator('[data-action="close-modal"]').first().click();
   await page.locator('[data-action="new-token"]').click();await page.locator('[name="label"]').fill('新 token');await page.locator('[name="config_keys"]').check();await page.locator('[name="transport_mode"]').selectOption('QUERY');await switchTo('zh-Hans');await switchTo('en');assert.equal(await page.locator('[name="config_keys"]').isChecked(),true);assert.equal(await page.locator('[name="transport_mode"]').inputValue(),'QUERY');await page.locator('[data-action="close-modal"]').first().click();
  }
 }
 await page.locator('[data-page="assets"]').first().click();await page.locator('.shell').waitFor();await page.locator('[data-action="edit-asset"]').click();await page.locator('#asset-form').waitFor();
 await page.locator('[name="notes"]').fill('冲突草稿保持原文');
 await page.evaluate(()=>{const form=document.querySelector('#asset-form');conflictForm(form,{status:409,data:{code:'version_conflict',remote:{id:'a1',name:'服务器版本',notes:'远端原文'},remote_version:3}},'asset')});
 const conflictBefore=await page.evaluate(()=>JSON.stringify(state.pendingConflict));
 await switchTo('zh-Hans');await switchTo('en');
 assert.equal(await page.evaluate(()=>JSON.stringify(state.pendingConflict)),conflictBefore);
 assert.match(await page.locator('.conflict-cols').innerText(),/冲突草稿保持原文/);
 assert.match(await page.locator('.conflict-cols').innerText(),/远端原文/);
 await page.evaluate(()=>closeModal());
 await page.evaluate(()=>showDeleteConflict({name:'保留名称'},{data:{remote:{name:'服务器保留名称'},remote_version:4}},'asset','a1'));
 const deleteBefore=await page.evaluate(()=>JSON.stringify(state.pendingDelete));await switchTo('zh-Hans');await switchTo('en');
 assert.equal(await page.evaluate(()=>JSON.stringify(state.pendingDelete)),deleteBefore);
 await page.evaluate(()=>closeModal());
 await page.evaluate(()=>issuedTokenModal({token:'secret-visible-once',config_keys:['c1']}));
 await switchTo('zh-Hans');await switchTo('en');assert.equal(await page.locator('#issued-token').innerText(),'secret-visible-once');await page.locator('[data-action="close-modal"]').first().click();
 // Validation and unknown API messages must update with the language without discarding drafts.
 await page.locator('[data-action="new-asset"]').click();await page.locator('[name="name"]').fill('保留错误草稿');
 await page.evaluate(()=>showFormError(document.querySelector('#form-error'),{status:422,data:{code:'future_code',error:'must never be shown'}}));
 await switchTo('zh-Hans');assert.match(await page.locator('#form-error').innerText(),/请求失败/);await switchTo('en');assert.match(await page.locator('#form-error').innerText(),/Request failed/);assert.equal(await page.locator('[name="name"]').inputValue(),'保留错误草稿');await page.locator('[data-action="close-modal"]').first().click();
 assert.deepEqual(errors,[]);
 assert.equal(requests.filter(r=>r.method!=='GET').length,1,'only login may write');
 await page.setViewportSize({width:390,height:844});await page.locator('[data-action="menu"]').click();await page.locator('[data-page="assets"]').first().click();await page.locator('.shell').waitFor();await page.locator('[data-action="new-asset"]').click();await switchTo('zh-Hans');await switchTo('en');
 assert.ok(await page.locator('.modal').evaluate(el=>el.scrollWidth<=el.clientWidth+1));
 assert.deepEqual(errors,[]);
 assert.equal(requests.filter(r=>r.method!=='GET').length,1);
 await page.evaluate(()=>closeModal());await switchTo('');
 assert.equal(await page.evaluate(()=>localStorage.getItem('sx_language')),null);
});
