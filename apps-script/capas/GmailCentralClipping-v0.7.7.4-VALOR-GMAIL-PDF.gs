/**
 * PRINCIPAIS CAPAS v0.7.7.4
 *
 * Regra principal: o Gmail só entrega um "Leia mais" para um jornal quando o
 * campo VEÍCULO do mesmo bloco é exatamente uma das formas autorizadas abaixo.
 * Nada de classificar pelo texto da matéria, por manchetes próximas ou por
 * palavras soltas do restante do e-mail.
 */

const ACCESS_KEY = 'PC26-8F2D4A7B-31C9E6F0-5A1D';
const VALOR_EMAIL_SENDER = 'diguinhodess@gmail.com';
const VALOR_EMAIL_SUBJECT = 'Monitoramento: CAPAS DE JORNAIS';

const VEHICLES = {
  'O GLOBO': [
    'O GLOBO - IMPRESSO - FLIP',
    'O GLOBO FTP - IMPRESSO - FLIP'
  ],
  'FOLHA DE SÃO PAULO': [
    'FOLHA DE SAO PAULO - IMPRESSO - FLIP',
    'FOLHA DE S PAULO - IMPRESSO - FLIP'
  ],
  'ESTADÃO': [
    'ESTADAO FTP - IMPRESSO - FLIP',
    'O ESTADO DE S. PAULO - IMPRESSO - FLIP',
    'ESTADAO - IMPRESSO - FLIP'
  ],
  'CORREIO BRAZILIENSE': [
    'CORREIO BRAZILIENSE - IMPRESSO - FLIP',
    'CORREIO BRAZILIENSE FTP - IMPRESSO - FLIP'
  ],
  'ESTADO DE MINAS': [
    'ESTADO DE MINAS - IMPRESSO - FLIP'
  ],
  'THE NEW YORK TIMES': [
    'NEW YORK TIMES - IMPRESSO - FLIP'
  ]
};

function doGet(e) {
  try {
    const key = e && e.parameter ? String(e.parameter.key || '') : '';
    if (key !== ACCESS_KEY) return json_({ok:false,error:'acesso negado'});
    const action = String((e.parameter && e.parameter.action) || 'matters').toLowerCase();
    if (action === 'health') {
      return json_({ok:true,version:'0.7.7.4',mode:'gmail-multi-email-plus-valor-pdf',mailbox:safeMailbox_()});
    }
    const requestedDate = String((e.parameter && e.parameter.date) || '');
    if (action === 'valor_manifest') return valorManifest_(requestedDate);
    if (action === 'valor_pdf') return valorPdf_(requestedDate, Number((e.parameter && e.parameter.page) || 0));
    return mattersFeed_(requestedDate);
  } catch (err) {
    return json_({ok:false,error:String(err && err.message ? err.message : err)});
  }
}

function mattersFeed_(requestedDate) {
  const tz = Session.getScriptTimeZone() || 'America/Sao_Paulo';
  const target = parseRequestedDate_(requestedDate, tz);
  const current = new Date(Date.UTC(target.y, target.m - 1, target.d, 12, 0, 0));
  const from = new Date(current.getTime() - 2 * 86400000);
  const until = new Date(current.getTime() + 2 * 86400000);

  // v0.7.7.1: NÃO usamos um subject:"..." único na busca do Gmail.
  // O Central Clipping pode repartir as páginas em vários e-mails, por exemplo:
  //   Monitoramento: CAPAS DE JORNAIS
  //   Monitoramento: Capa de Jornais 1
  //   Monitoramento : Capa de Jornais 2
  //   CAPA DE JORNAIS 1 APP
  //   CAPA DE JORNAIS 2 APP
  // Buscamos todas as mensagens do remetente na janela da data e filtramos o
  // assunto de forma tolerante (singular/plural, espaços no ':' e número final).
  const query = 'from:contato@centralclipping.com.br ' +
    'after:' + Utilities.formatDate(from,'UTC','yyyy/MM/dd') + ' before:' + Utilities.formatDate(until,'UTC','yyyy/MM/dd');

  const threads = searchAllThreads_(query);
  const candidates = {};
  let messagesScanned=0, messagesIgnoredBySubject=0, messagesIgnoredByWindow=0, noticiaBlocks=0, rawLeiaMaisFound=0,
      datedItemsMatched=0, exactVehicleMatches=0, matchingThreads=0;
  const matchedSubjects=[];

  threads.forEach(function(thread){
    let threadMatched=false;
    thread.getMessages().forEach(function(msg){
      const msgTime=msg.getDate().getTime();
      if(msgTime<from.getTime() || msgTime>=until.getTime()){
        messagesIgnoredByWindow++;
        return;
      }
      const subject=String(msg.getSubject()||'').trim();
      if(!isCoverMonitorSubject_(subject)){
        messagesIgnoredBySubject++;
        return;
      }
      threadMatched=true;
      messagesScanned++;
      if(matchedSubjects.indexOf(subject)<0) matchedSubjects.push(subject);

      const html = msg.getBody() || '';
      const stats = collectExactBlocks_(html, candidates, target.dash);
      noticiaBlocks += stats.noticiaBlocks;
      rawLeiaMaisFound += stats.rawLeiaMaisFound;
      datedItemsMatched += stats.datedItemsMatched;
      exactVehicleMatches += stats.exactVehicleMatches;

      // Fallback robusto também roda em CADA e-mail complementar.
      const fallbackStats = collectExactVehicleWindows_(html, candidates, target.dash);
      rawLeiaMaisFound += fallbackStats.rawLeiaMaisFound;
      datedItemsMatched += fallbackStats.datedItemsMatched;
      exactVehicleMatches += fallbackStats.exactVehicleMatches;
    });
    if(threadMatched) matchingThreads++;
  });

  const matters=[];
  const counts={};
  Object.keys(VEHICLES).forEach(function(name){
    const list=(candidates[name]||[]).slice().sort(function(a,b){
      if (b.priority !== a.priority) return b.priority-a.priority;
      return a.order-b.order;
    });
    // Só eliminamos o MESMO link Leia mais repetido em mais de um e-mail.
    // Nenhum limite artificial de 5/10 páginas: Android/Windows analisam todas.
    const seen={}; const unique=[];
    list.forEach(function(x){ if(x.url && !seen[x.url]){ seen[x.url]=true; unique.push(x); } });
    counts[name]=unique.length;
    unique.forEach(function(x,index){
      matters.push({name:name,matterUrl:x.url,vehicle:x.vehicle,priority:x.priority,candidateIndex:index+1});
    });
  });

  return json_({
    ok:true,version:'0.7.7.4',date:target.dash,mailbox:safeMailbox_(),
    threads:matchingThreads,threadsSearched:threads.length,messagesScanned:messagesScanned,
    messagesIgnoredBySubject:messagesIgnoredBySubject,messagesIgnoredByWindow:messagesIgnoredByWindow,subjectsMatched:matchedSubjects,
    noticiaBlocks:noticiaBlocks,rawLeiaMaisFound:rawLeiaMaisFound,datedItemsMatched:datedItemsMatched,
    exactVehicleMatches:exactVehicleMatches,count:matters.length,counts:counts,matters:matters
  });
}


/**
 * Valor Econômico — PDFs autorizados enviados por e-mail.
 *
 * Só aceitamos:
 * - remetente exato diguinhodess@gmail.com;
 * - assunto exato Monitoramento: CAPAS DE JORNAIS (comparação normalizada);
 * - anexo PDF com nome Valor-Economico-AAAA-MM-DD-Pagina-N.pdf;
 * - data do nome do arquivo igual à data solicitada pelo app.
 *
 * Se houver reenvio/correção no mesmo dia, vence o anexo da mensagem mais recente.
 */
function valorManifest_(requestedDate){
  const found=findValorPdfAttachments_(requestedDate);
  return json_({
    ok:true,
    version:'0.7.7.4',
    date:found.date,
    mailbox:safeMailbox_(),
    sender:VALOR_EMAIL_SENDER,
    subject:VALOR_EMAIL_SUBJECT,
    count:found.items.length,
    pages:found.items.map(function(x){
      return {page:x.page,filename:x.filename,messageDate:x.messageDate};
    })
  });
}

function valorPdf_(requestedDate,page){
  if(!page || page<1 || page>20) return json_({ok:false,error:'Página do Valor inválida'});
  const found=findValorPdfAttachments_(requestedDate);
  let item=null;
  for(let i=0;i<found.items.length;i++) if(found.items[i].page===page){item=found.items[i];break;}
  if(!item) return json_({ok:false,error:'PDF Página '+page+' do Valor não encontrado para '+found.date});

  const bytes=item.blob.getBytes();
  return json_({
    ok:true,
    version:'0.7.7.4',
    date:found.date,
    page:item.page,
    filename:item.filename,
    mimeType:'application/pdf',
    size:bytes.length,
    dataBase64:Utilities.base64Encode(bytes)
  });
}

function findValorPdfAttachments_(requestedDate){
  const tz=Session.getScriptTimeZone()||'America/Sao_Paulo';
  const target=parseRequestedDate_(requestedDate,tz);
  const current=new Date(Date.UTC(target.y,target.m-1,target.d,12,0,0));
  const from=new Date(current.getTime()-2*86400000);
  const until=new Date(current.getTime()+2*86400000);
  const query='from:'+VALOR_EMAIL_SENDER+' has:attachment '+
    'after:'+Utilities.formatDate(from,'UTC','yyyy/MM/dd')+' before:'+Utilities.formatDate(until,'UTC','yyyy/MM/dd');

  const byPage={};
  searchAllThreads_(query).forEach(function(thread){
    thread.getMessages().forEach(function(msg){
      if(normalizeText_(msg.getSubject()||'')!==normalizeText_(VALOR_EMAIL_SUBJECT)) return;
      const messageTime=msg.getDate().getTime();
      const attachments=msg.getAttachments({includeInlineImages:false,includeAttachments:true})||[];
      attachments.forEach(function(blob){
        const filename=String(blob.getName()||'').trim();
        const m=/^Valor[-_ ]Economico[-_ ](\d{4}-\d{2}-\d{2})[-_ ]Pagina[-_ ](\d+)\.pdf$/i.exec(filename);
        if(!m || m[1]!==target.dash) return;
        const page=Number(m[2]);
        if(!page || page<1 || page>20) return;
        const currentItem=byPage[page];
        if(currentItem && currentItem.messageTime>messageTime) return;
        byPage[page]={
          page:page,
          filename:filename,
          blob:blob,
          messageTime:messageTime,
          messageDate:Utilities.formatDate(msg.getDate(),tz,'yyyy-MM-dd HH:mm:ss')
        };
      });
    });
  });

  const items=Object.keys(byPage).map(function(k){return byPage[k];});
  items.sort(function(a,b){return a.page-b.page;});
  return {date:target.dash,items:items};
}

function searchAllThreads_(query){
  const out=[];
  const pageSize=100;
  let start=0;
  while(true){
    const batch=GmailApp.search(query,start,pageSize);
    if(!batch || batch.length===0) break;
    Array.prototype.push.apply(out,batch);
    if(batch.length<pageSize) break;
    start+=batch.length;
  }
  return out;
}

function isCoverMonitorSubject_(subject){
  const s=normalizeText_(subject);

  // Formatos antigos e novos usados pelo Central Clipping:
  //   Monitoramento: CAPAS DE JORNAIS
  //   Monitoramento : Capa de Jornais 1
  //   CAPA DE JORNAIS 1 APP
  //   CAPA DE JORNAIS 2 APP
  // Também aceitamos APP antes do número para tolerar futuras variações.
  if (/^(?:MONITORAMENTO )?CAPAS? DE JORNAIS(?: [0-9]+)?(?: APP)?$/.test(s)) return true;
  if (/^(?:MONITORAMENTO )?CAPAS? DE JORNAIS APP(?: [0-9]+)?$/.test(s)) return true;
  return false;
}

function collectExactBlocks_(html,out,targetDash){
  const source=String(html||'').replace(/&amp;/gi,'&').replace(/&#38;/gi,'&').replace(/\\u0026/gi,'&');
  const starts=[]; const rx=/<div\b[^>]*class=["'][^"']*\bnoticia\b[^"']*["'][^>]*>/ig; let m;
  while((m=rx.exec(source))!==null) starts.push(m.index);
  let raw=0,dated=0,exact=0,order=0;

  if(starts.length===0) return collectPlainFallback_(source,out,targetDash);

  for(let i=0;i<starts.length;i++){
    const end=(i+1<starts.length)?starts[i+1]:Math.min(source.length,starts[i]+16000);
    const block=source.substring(starts[i],end);
    const url=extractLeiaMais_(block); if(!url) continue; raw++;
    const dateText=firstClassText_(block,'data');
    let itemDate=parseNewsletterDate_(dateText);
    if(!itemDate && containsTargetDateText_(block,targetDash)) itemDate=targetDash;
    if(itemDate!==targetDash) continue; dated++;

    const vehicle=firstClassText_(block,'veiculo');
    const name=exactVehicleName_(vehicle); if(!name) continue; exact++;
    const subtitle=firstClassText_(block,'subtitulo');
    const title=firstClassText_(block,'titulo');
    const priority=vehiclePriority_(name,vehicle,title,subtitle);
    if(!out[name]) out[name]=[];
    out[name].push({url:cleanupUrl_(url),vehicle:vehicle,priority:priority,order:order++});
  }
  return {noticiaBlocks:starts.length,rawLeiaMaisFound:raw,datedItemsMatched:dated,exactVehicleMatches:exact};
}

// Fallback para eventual e-mail em texto/HTML sem class="noticia". Ainda assim,
// exige nome de veículo EXATO imediatamente antes do Leia mais.
function collectPlainFallback_(source,out,targetDash){
  const lm=/<a\b[^>]*href=["'](https:\/\/centralclipping\.com\.br\/monitoramento\/materia\/\d+\/\d+)["'][^>]*>[\s\S]*?Leia\s*mais[\s\S]*?<\/a>/ig;
  let m,raw=0,dated=0,exact=0,order=0;
  while((m=lm.exec(source))!==null){
    raw++;
    const before=source.substring(Math.max(0,m.index-1800),m.index);
    if(!containsTargetDateText_(before,targetDash)) continue; dated++;
    const lines=stripHtml_(before).split(/\s{2,}|\n/).map(function(x){return x.trim();}).filter(Boolean);
    let foundName='',vehicle='';
    for(let i=lines.length-1;i>=0 && i>=lines.length-8;i--){
      const n=exactVehicleName_(lines[i]); if(n){foundName=n;vehicle=lines[i];break;}
    }
    if(!foundName) continue; exact++;
    if(!out[foundName]) out[foundName]=[];
    out[foundName].push({url:cleanupUrl_(m[1]),vehicle:vehicle,priority:vehiclePriority_(foundName,vehicle,'',''),order:order++});
  }
  return {noticiaBlocks:0,rawLeiaMaisFound:raw,datedItemsMatched:dated,exactVehicleMatches:exact};
}

function exactVehicleName_(vehicle){
  const v=normalizeText_(vehicle);
  const names=Object.keys(VEHICLES);
  for(let i=0;i<names.length;i++){
    const name=names[i];
    const allowed=VEHICLES[name];
    for(let j=0;j<allowed.length;j++){
      // IMPORTANTE: normaliza também a forma autorizada. A versão anterior
      // normalizava apenas o texto vindo do Gmail, então hífens, pontos e
      // acentos faziam um nome correto não bater com a lista.
      if(v===normalizeText_(allowed[j])) return name;
    }
  }
  return '';
}

function vehiclePriority_(name,vehicle,title,subtitle){
  const v=normalizeText_(vehicle), t=normalizeText_((title||'')+' '+(subtitle||''));
  let p=100;
  if(name==='O GLOBO'){
    if(v==='O GLOBO IMPRESSO FLIP') p+=40;
    if(v==='O GLOBO FTP IMPRESSO FLIP') p+=30;
    if(t.indexOf('IRINEU MARINHO')>=0 || t.indexOf('O GLOBO')>=0) p+=100;
  } else if(name==='FOLHA DE SÃO PAULO'){
    if(v==='FOLHA DE SAO PAULO IMPRESSO FLIP') p+=40;
    if(t.indexOf('DESDE 1921')>=0) p+=120;
  } else if(name==='ESTADÃO'){
    // O texto do e-mail serve somente para garantir que candidatos importantes
    // entrem no lote. A escolha final é feita depois, pela imagem original.
    if(t.indexOf('O ESTADO DE S PAULO')>=0 || t.indexOf('FUNDADO EM 1875')>=0) p+=180;
    // Chamadas curtas da capa podem terminar em A2/A12/B4 etc. Isso NÃO significa
    // que a imagem seja uma página interna; é comum ser apenas a página indicada
    // pela chamada. Mantemos esse tipo de item entre os candidatos enviados.
    if(t.length<=140 && /\b[A-D]\s?\d{1,2}\b/.test(t)) p+=170;
    if(t.indexOf('MEDICINA E ESTUDOS')>=0) p-=300;
  } else if(name==='CORREIO BRAZILIENSE'){
    // O nome exato é obrigatório. Se o clipping disser PÁGINA 17, o APK fará
    // a validação do masthead e rejeitará a página interna, caindo no site do Correio.
    if(/\bPAGINA\s+\d+\b/.test(t)) p-=200;
  } else if(name==='ESTADO DE MINAS'){
    if(t.indexOf('ESTADO DE MINAS')>=0 || t.indexOf('DO ZERO AOS MILHOES')>=0) p+=80;
  } else if(name==='THE NEW YORK TIMES'){
    // v0.7.5.2: diferencia a capa real de páginas internas que trazem apenas
    // o copyright "The New York Times Company". No boletim de 24/08/2026,
    // a página interna vinha antes da capa e as duas recebiam a mesma prioridade.
    const strongCover =
      /THE NEW YORK TIMES (MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY)/.test(t) ||
      t.indexOf('ALL THE NEWS THAT S FIT TO PRINT')>=0 ||
      t.indexOf('ALL THE NEWS THAT IS FIT TO PRINT')>=0;
    if(strongCover) p+=260;
    else if(t.indexOf('THE NEW YORK TIMES COMPANY')>=0) p-=140;
    else if(t.indexOf('NEW YORK TIMES')>=0) p+=40;
  }
  return p;
}


/**
 * Fallback robusto por janela de veículo.
 *
 * Converte o HTML em linhas, mas preserva cada href como "HREF:<url>".
 * Assim não dependemos da classe CSS usada no boletim. Só associamos uma URL
 * quando a sequência é:
 *   data selecionada -> nome EXATO do veículo -> próximo link Leia mais.
 *
 * Isso corrige especialmente O Globo, que em alguns boletins vinha em um bloco
 * HTML diferente embora o texto "O Globo FTP - Impresso - Flip" estivesse certo.
 */
function collectExactVehicleWindows_(html,out,targetDash){
  let tagged = String(html||'')
    .replace(/&amp;/gi,'&')
    .replace(/&#38;/gi,'&')
    .replace(/\\u0026/gi,'&');

  tagged = tagged.replace(
    /<a\b([^>]*?)href=["']([^"']+)["']([^>]*)>([\s\S]*?)<\/a>/gi,
    function(_,a,url,b,label){
      return '\n'+stripHtml_(label)+'\nHREF:'+cleanupUrl_(url)+'\n';
    }
  );

  tagged = tagged
    .replace(/<(?:br|\/div|\/p|\/td|\/tr|\/li|\/section|\/article)\b[^>]*>/gi,'\n')
    .replace(/<(?:div|p|td|tr|li|section|article)\b[^>]*>/gi,'\n');

  const text = stripHtmlKeepNewlines_(tagged);
  const lines = text.split(/\n+/).map(function(x){return x.trim();}).filter(Boolean);

  let currentDate='';
  let currentName='';
  let currentVehicle='';
  let context=[];
  let raw=0, dated=0, exact=0, order=100000;

  for(let i=0;i<lines.length;i++){
    const line=lines[i];
    const parsedDate=parseNewsletterDate_(line);
    if(parsedDate){
      currentDate=parsedDate;
      currentName='';
      currentVehicle='';
      context=[];
      continue;
    }

    const name=exactVehicleName_(line);
    if(name){
      currentName=name;
      currentVehicle=line;
      context=[];
      continue;
    }

    if(currentName){
      context.push(line);
      if(context.length>8) context.shift();
    }

    if(line.indexOf('HREF:')===0){
      const url=cleanupUrl_(line.substring(5));
      if(!/^https:\/\/centralclipping\.com\.br\/monitoramento\/materia\/\d+\/\d+/i.test(url)) continue;
      raw++;

      if(currentDate!==targetDash || !currentName) continue;
      dated++; exact++;

      const ctx=context.join(' ');
      const priority=vehiclePriority_(currentName,currentVehicle,'',ctx)+25;
      if(!out[currentName]) out[currentName]=[];
      out[currentName].push({
        url:url,
        vehicle:currentVehicle,
        priority:priority,
        order:order++
      });

      currentName='';
      currentVehicle='';
      context=[];
    }
  }

  return {rawLeiaMaisFound:raw,datedItemsMatched:dated,exactVehicleMatches:exact};
}

function stripHtmlKeepNewlines_(v){
  return String(v||'')
    .replace(/<style[\s\S]*?<\/style>/gi,' ')
    .replace(/<script[\s\S]*?<\/script>/gi,' ')
    .replace(/<[^>]+>/g,' ')
    .replace(/&nbsp;/gi,' ')
    .replace(/&amp;/gi,'&')
    .replace(/\r/g,'')
    .replace(/[ \t]+/g,' ')
    .replace(/ *\n */g,'\n')
    .trim();
}

function extractLeiaMais_(block){
  let m=/<a\b[^>]*href=["'](https:\/\/centralclipping\.com\.br\/monitoramento\/materia\/\d+\/\d+)["'][^>]*>[\s\S]*?Leia\s*mais[\s\S]*?<\/a>/i.exec(block);
  if(m) return cleanupUrl_(m[1]);
  if(normalizeText_(block).indexOf('LEIA MAIS')>=0){
    m=/https:\/\/centralclipping\.com\.br\/monitoramento\/materia\/\d+\/\d+/i.exec(block);
    if(m) return cleanupUrl_(m[0]);
  }
  return '';
}

function firstClassText_(html,className){
  const rx=new RegExp('<div\\b[^>]*class=["\\\'][^"\\\']*\\b'+className+'\\b[^"\\\']*["\\\'][^>]*>([\\s\\S]*?)<\\/div>','i');
  const m=rx.exec(html); return m?stripHtml_(m[1]||''):'';
}

function parseNewsletterDate_(text){
  const s=normalizeText_(text);
  const months={JANEIRO:1,FEVEREIRO:2,MARCO:3,ABRIL:4,MAIO:5,JUNHO:6,JULHO:7,AGOSTO:8,SETEMBRO:9,OUTUBRO:10,NOVEMBRO:11,DEZEMBRO:12};
  let m=/(\d{1,2})\s+DE\s+(JANEIRO|FEVEREIRO|MARCO|ABRIL|MAIO|JUNHO|JULHO|AGOSTO|SETEMBRO|OUTUBRO|NOVEMBRO|DEZEMBRO)\s+DE\s+(\d{4})/.exec(s);
  if(m) return m[3]+'-'+pad2_(months[m[2]])+'-'+pad2_(Number(m[1]));
  m=/(\d{1,2})[\/-](\d{1,2})[\/-](\d{4})/.exec(s);
  if(m) return m[3]+'-'+pad2_(Number(m[2]))+'-'+pad2_(Number(m[1]));
  return '';
}

function containsTargetDateText_(html,targetDash){
  const p=targetDash.split('-'); if(p.length!==3) return false;
  const y=Number(p[0]),m=Number(p[1]),d=Number(p[2]);
  const names=['','JANEIRO','FEVEREIRO','MARCO','ABRIL','MAIO','JUNHO','JULHO','AGOSTO','SETEMBRO','OUTUBRO','NOVEMBRO','DEZEMBRO'];
  const s=normalizeText_(html);
  return s.indexOf(d+' DE '+names[m]+' DE '+y)>=0 || s.indexOf(pad2_(d)+' '+pad2_(m)+' '+y)>=0;
}

function parseRequestedDate_(value,tz){
  const raw=String(value||'').trim(); let y,m,d; const x=/^(\d{4})-(\d{2})-(\d{2})$/.exec(raw);
  if(x){y=Number(x[1]);m=Number(x[2]);d=Number(x[3]);}
  else {const now=new Date();y=Number(Utilities.formatDate(now,tz,'yyyy'));m=Number(Utilities.formatDate(now,tz,'MM'));d=Number(Utilities.formatDate(now,tz,'dd'));}
  if(y<2000||y>2100||m<1||m>12||d<1||d>31) throw new Error('Data inválida. Use yyyy-MM-dd.');
  return {y:y,m:m,d:d,dash:y+'-'+pad2_(m)+'-'+pad2_(d)};
}

function cleanupUrl_(u){return String(u||'').replace(/&amp;/gi,'&').replace(/&#38;/gi,'&').replace(/\\u0026/gi,'&').replace(/\\\//g,'/').trim();}
function stripHtml_(v){return String(v||'').replace(/<style[\s\S]*?<\/style>/gi,' ').replace(/<script[\s\S]*?<\/script>/gi,' ').replace(/<br\s*\/?>/gi,'\n').replace(/<[^>]+>/g,' ').replace(/&nbsp;/gi,' ').replace(/&amp;/gi,'&').replace(/[ \t]+/g,' ').trim();}
function normalizeText_(v){let s=stripHtml_(v);try{s=s.normalize('NFD').replace(/[\u0300-\u036f]/g,'');}catch(_){}return s.toUpperCase().replace(/[^A-Z0-9]+/g,' ').replace(/\s+/g,' ').trim();}
function safeMailbox_(){try{return Session.getEffectiveUser().getEmail()||'';}catch(_){return '';}}
function pad2_(n){return ('0'+Number(n)).slice(-2);}
function json_(obj){return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);}
function autorizarPermissoes(){GmailApp.search('from:contato@centralclipping.com.br',0,1);Logger.log('Permissão Gmail autorizada para: '+safeMailbox_());}
