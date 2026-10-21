// Language resources belong to this browser. Business data and credentials are never touched.
(() => {
  'use strict';
  const supported=['zh-Hans','en'];
  const storageKey='sx_language';
  function matchLanguage(preferences=[]) {
    for (const value of preferences) {
      try {
        const locale=new Intl.Locale(value);
        if (locale.language==='en') return 'en';
        if (locale.language==='zh'&&locale.maximize().script==='Hans') return 'zh-Hans';
      } catch (_) { /* Ignore invalid browser language tags. */ }
    }
    return 'zh-Hans';
  }
  function readPreference() {
    try { const value=localStorage.getItem(storageKey); return supported.includes(value)?value:''; }
    catch (_) { return ''; }
  }
  let preference=readPreference();
  let language=preference||matchLanguage(navigator.languages?.length?navigator.languages:[navigator.language]);
  const locale=()=>language==='en'?'en':'zh-Hans-CN';
  function tr(key,parameters={}) {
    let message=globalThis.SuirenXMessages[language]?.[key]??globalThis.SuirenXMessages['zh-Hans'][key];
    if (message===undefined) throw new Error('Missing translation: '+key);
    if (typeof message==='object') message=message[new Intl.PluralRules(locale()).select(Number(parameters.count))]??message.other;
    return message.replace(/\{(\w+)\}/g,(_,name)=>String(parameters[name]??''));
  }
  function setPreference(value) {
    preference=supported.includes(value)?value:'';
    try { if(preference)localStorage.setItem(storageKey,preference);else localStorage.removeItem(storageKey); } catch (_) { /* Session-only selection is still usable. */ }
    language=preference||matchLanguage(navigator.languages?.length?navigator.languages:[navigator.language]);
    document.documentElement.lang=language;
    document.title=tr('app.title');
  }
  function number(value) { return new Intl.NumberFormat(locale()).format(Number(value)||0); }
  function days(value) { return tr('format.days',{count:Number(value)||0,value:number(value)}); }
  function money(cents) { return new Intl.NumberFormat(locale(),{style:'currency',currency:'CNY',currencyDisplay:language==='en'?'code':'symbol'}).format((Number(cents)||0)/100); }
  function dateOnly(value) {
    if (!value) return '—';
    const match=/^(\d{4})-(\d{2})-(\d{2})/.exec(value);
    if(!match)return '—';
    const date=new Date(Date.UTC(Number(match[1]),Number(match[2])-1,Number(match[3])));
    return new Intl.DateTimeFormat(locale(),{year:'numeric',month:'2-digit',day:'2-digit',timeZone:'UTC'}).format(date);
  }
  function dateTime(value) {
    if(!value)return '—';
    const date=new Date(value);
    return Number.isNaN(date.getTime())?'—':new Intl.DateTimeFormat(locale(),{year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).format(date);
  }
  const errorKeys={
    invalid_token:'error.sessionExpired',
    invalid_credentials:'error.credentials',
    account_disabled:'error.accountDisabled',
    username_taken:'error.usernameTaken',
    admin_login_unavailable:'error.adminUnavailable',
    superadmin_required:'error.adminRequired',
    too_many_attempts:'error.rateLimited',
    rate_limited:'error.rateLimited',
    version_conflict:'error.versionConflict',
    invalid_account_request:'error.accountRequest',
    invalid_account_status:'error.accountRequest',
    invalid_web_request:'error.recordRequest',
    invalid_asset:'error.recordRequest',
    invalid_expiry:'error.recordRequest',
    invalid_config:'error.configRequest',
    invalid_api_token:'error.tokenRequest',
    invalid_config_limit:'error.configLimit',
    invalid_config_key:'error.configRequest',
    invalid_registration_setting:'error.registrationSetting',
    account_registration_disabled:'error.registrationDisabled',
    not_found:'error.notFound',
    account_not_found:'error.notFound',
    config_not_found:'error.notFound',
    api_token_not_found:'error.notFound',
    asset_not_found:'error.notFound',
    expiry_not_found:'error.notFound',
    internal_server_error:'error.server',
    registration_closed:'error.registrationDisabled',
    invalid_request:'error.invalidRequest',
    sync_conflict:'error.versionConflict',
    restore_before_edit:'error.restoreFirst',
    last_superadmin:'error.lastAdmin',
    invalid_admin_input:'error.invalidRequest',
    config_name_taken:'error.configNameTaken',
    api_token_revoked:'error.tokenRevoked',
    token_scope_denied:'error.tokenScope',
    api_token_expired:'error.tokenExpired',
    internal_error:'error.server',
    request_too_large:'error.tooLarge',
    config_too_large:'error.tooLarge',
  };
  function errorText(error) {
    if(!error?.status)return tr('error.network');
    const code=typeof error.data?.code==='string'?error.data.code:'';
    if(Object.hasOwn(errorKeys,code))return tr(errorKeys[code]);
    const safe=/^[a-zA-Z0-9_.-]{1,80}$/.test(code)?code:String(error.status);
    return tr('error.unknown',{code:safe});
  }
  globalThis.SuirenXI18n={tr,locale,number,days,money,dateOnly,dateTime,errorText,matchLanguage,setPreference,get language(){return language},get preference(){return preference}};
  setPreference(preference);
})();
