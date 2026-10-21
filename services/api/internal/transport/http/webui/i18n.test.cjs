const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');

function runtime({languages=['zh-CN'],stored='',storageDenied=false}={}){
  const storage=new Map(stored?[['sx_language',stored]]:[]);
  const context=vm.createContext({Intl,navigator:{languages,language:languages[0]},document:{documentElement:{lang:''},title:''},localStorage:{getItem:key=>{if(storageDenied)throw Error('denied');return storage.get(key)},setItem:(key,value)=>{if(storageDenied)throw Error('denied');storage.set(key,value)},removeItem:key=>{if(storageDenied)throw Error('denied');storage.delete(key)}}});
  for(const file of ['zh-Hans.js','en.js','i18n.js'])vm.runInContext(fs.readFileSync(path.join(__dirname,file),'utf8'),context,{filename:file});
  return {i18n:context.SuirenXI18n,messages:context.SuirenXMessages,context,storage};
}

test('browser preferences respect priority and Chinese script, with a deterministic fallback',()=>{
  const {i18n}=runtime();
  for(const [preferences,expected] of [
    [['fr-FR','en-GB','zh-CN'],'en'],[['zh-Hant','en-US'],'en'],[['zh-TW','en-US'],'en'],
    [['zh-Hant-CN','en'],'en'],[['zh-Hans-TW'],'zh-Hans'],[['zh-SG'],'zh-Hans'],
    [['zh'],'zh-Hans'],[['zh-HK','fr'],'zh-Hans'],[['en-Latn-US'],'en'],[['invalid_tag','en'],'en'],[[],'zh-Hans']
  ])assert.equal(i18n.matchLanguage(preferences),expected,preferences.join(','));
});

test('explicit language is browser-local; follow browser removes the override',()=>{
  const {i18n,storage,context}=runtime({languages:['en-AU'],stored:'zh-Hans'});
  assert.equal(i18n.language,'zh-Hans');
  i18n.setPreference('en');
  assert.equal(storage.get('sx_language'),'en');
  assert.equal(context.document.documentElement.lang,'en');
  assert.equal(context.document.title,'SuirenX · Asset workspace');
  i18n.setPreference('');
  assert.equal(storage.has('sx_language'),false);
  assert.equal(i18n.language,'en');
  assert.equal(runtime({languages:['en'],stored:'fr'}).i18n.language,'en');
  assert.equal(runtime({languages:['en'],storageDenied:true}).i18n.language,'en');
});

test('plural forms and parameters are locale-aware and business currency remains CNY',()=>{
  const {i18n}=runtime({languages:['en']});
  assert.equal(i18n.days(1),'1 day');
  assert.equal(i18n.days(2),'2 days');
  assert.equal(i18n.tr('format.items',{count:1,value:'1'}),'1 item');
  assert.equal(i18n.tr('format.items',{count:0,value:'0'}),'0 items');
  assert.match(i18n.money(123456),/1,234\.56/);
  assert.match(i18n.money(123456),/CNY/);
  assert.doesNotMatch(i18n.money(123456),/\$/);
  assert.equal(i18n.tr('delete.confirm',{name:'我的电脑'}),'Permanently delete “我的电脑”? This is separate from archiving and retains a sync tombstone.');
  i18n.setPreference('zh-Hans');
  assert.equal(i18n.days(1),'1 天');
  assert.equal(i18n.tr('format.items',{count:2,value:'2'}),'2 项');
});

test('date-only formatting cannot shift dates through the machine timezone',()=>{
  const {i18n}=runtime({languages:['en']});
  assert.equal(i18n.dateOnly('2026-01-01'),'01/01/2026');
  assert.equal(i18n.dateOnly('2026-10-20'),'10/20/2026');
  i18n.setPreference('zh-Hans');
  assert.equal(i18n.dateOnly('2026-10-20'),'2026/10/20');
  assert.equal(i18n.dateOnly(''),'—');
  assert.equal(i18n.dateOnly('invalid'),'—');
});

test('server errors are localized by code and never expose arbitrary server prose',()=>{
  const {i18n}=runtime({languages:['en']});
  assert.equal(i18n.errorText({status:401,data:{code:'invalid_credentials',error:'sensitive raw server message'}}),'Incorrect username or password.');
  assert.equal(i18n.errorText({status:422,data:{code:'new_server_code',error:'raw prose'}}),'Request failed. Try again (error code: new_server_code).');
  assert.equal(i18n.errorText({status:500,data:{code:'<script>raw</script>',error:'raw prose'}}),'Request failed. Try again (error code: 500).');
  for(const code of ['constructor','__proto__','toString'])assert.equal(i18n.errorText({status:422,data:{code}}),`Request failed. Try again (error code: ${code}).`);
  assert.equal(i18n.errorText(new Error('Fetch failed')),'Check your connection and try again.');
  i18n.setPreference('zh-Hans');
  assert.equal(i18n.errorText({status:409,data:{code:'restore_before_edit'}}),'归档记录必须先恢复，再编辑。');
});

test('dictionaries have matching semantic keys, parameters and complete explicit render coverage',()=>{
  const {messages}=runtime();
  assert.deepEqual(Object.keys(messages.en).sort(),Object.keys(messages['zh-Hans']).sort());
  const parameters=value=>[...new Set(JSON.stringify(value).match(/\{\w+\}/g)||[])].sort();
  for(const [key,message] of Object.entries(messages.en)){
    assert.match(key,/^[a-zA-Z][a-zA-Z0-9]*\.[a-zA-Z]+$/);
    assert.deepEqual(parameters(message),parameters(messages['zh-Hans'][key]),key);
    assert.ok(typeof message==='object'?message.other:message,key);
  }
  const source=fs.readFileSync(path.join(__dirname,'app.js'),'utf8')+fs.readFileSync(path.join(__dirname,'i18n.js'),'utf8');
  for(const match of source.matchAll(/\btr\('([^']+)'/g))assert.ok(messages.en[match[1]],match[1]);
  assert.doesNotMatch(source,/data\?\.(error|message)/);
});
