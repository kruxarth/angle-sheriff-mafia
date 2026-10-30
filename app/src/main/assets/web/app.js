/*
 * Angel Sheriff Mafia, browser edition. Served by the host phone over the local network.
 * The host sends this seat's view whenever the table changes (server-sent events), and the
 * page sends the player's moves back. Everything here is drawn from that one view.
 */
(function () {
  'use strict';

  var STORE = 'asm-seat';
  var $app = document.getElementById('app');
  var $banner = document.getElementById('banner');

  var S = {
    token: null,
    name: '',
    pron: '',
    hello: null,
    helloFailed: false,
    view: null,
    offset: 0, // host clock minus ours
    es: null,
    lost: false,
    gone: null, // why we were signed out
    error: null,
    busy: false,
    sel: null, // the name picked on the current grid, before confirming
    stageKey: '',
    hold: null, // which pad is held down: 'card', 'result' or 'peek'
    seenCard: false,
    lastHtml: '',
  };

  /* ---------- Storage ---------- */

  function load() {
    try {
      var saved = JSON.parse(localStorage.getItem(STORE) || 'null');
      if (saved && saved.token) {
        S.token = saved.token;
        S.name = saved.name || '';
      }
    } catch (e) { /* private mode: play without remembering */ }
  }

  function save() {
    try {
      if (S.token) localStorage.setItem(STORE, JSON.stringify({ token: S.token, name: S.name }));
      else localStorage.removeItem(STORE);
    } catch (e) { /* ignore */ }
  }

  /* ---------- Talking to the host ---------- */

  function post(path, body) {
    return fetch(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      cache: 'no-store',
    }).then(function (r) { return r.json(); });
  }

  function hello() {
    return fetch('/api/hello', { cache: 'no-store' })
      .then(function (r) { return r.json(); })
      .then(function (h) { S.hello = h; S.helloFailed = false; document.title = h.table + ' · Angel Sheriff Mafia'; })
      .catch(function () { S.helloFailed = true; });
  }

  function connect() {
    if (S.es) S.es.close();
    var es = new EventSource('/api/events?token=' + encodeURIComponent(S.token));
    S.es = es;
    es.addEventListener('view', function (e) {
      S.lost = false;
      onView(JSON.parse(e.data));
    });
    es.addEventListener('gone', function (e) {
      es.close();
      if (S.es === es) S.es = null;
      signOut(JSON.parse(e.data).reason);
    });
    es.onerror = function () {
      // EventSource keeps retrying by itself. Just say so.
      S.lost = true;
      showBanner();
    };
  }

  function join() {
    var name = S.name.trim();
    if (!name) { S.error = 'Type your name first.'; render(); return; }
    S.busy = true;
    S.error = null;
    render();
    post('/api/join', { name: name, pronunciation: S.pron.trim() || null, app: false })
      .then(function (res) {
        S.busy = false;
        if (res.token) {
          S.token = res.token;
          S.gone = null;
          save();
          connect();
        } else {
          S.error = res.error || 'The host said no.';
        }
        render();
      })
      .catch(function () {
        S.busy = false;
        S.error = "Couldn't reach the host. Are you on the same Wi-Fi or hotspot?";
        render();
      });
  }

  function send(action) {
    if (S.busy) return Promise.resolve(false);
    S.busy = true;
    S.error = null;
    render();
    return post('/api/act', { token: S.token, action: action })
      .then(function (res) {
        S.busy = false;
        if (!res.ok) S.error = res.error || "That didn't work.";
        render();
        return !!res.ok;
      })
      .catch(function () {
        S.busy = false;
        S.error = "Couldn't reach the host. Try again in a moment.";
        render();
        return false;
      });
  }

  function signOut(reason) {
    S.token = null;
    S.view = null;
    S.gone = reason;
    save();
    render();
  }

  function onView(v) {
    S.view = v;
    S.offset = v.now - Date.now();
    var g = v.game;
    var key = g ? [g.number, g.stage, g.day, g.vote && g.vote.revote ? 'r' : ''].join(':') : v.status;
    if (key !== S.stageKey) {
      S.stageKey = key;
      S.sel = null;
      S.error = null;
      if (!g || g.stage !== 'DEAL') S.seenCard = false;
      var me = g && g.me;
      var buzz = me && me.alive && ((g.stage === 'NIGHT' && g.night && g.night.canPick) || (g.stage === 'VOTE' && g.vote && g.vote.canVote));
      if (buzz && navigator.vibrate) { try { navigator.vibrate(120); } catch (e) { /* ignore */ } }
    }
    if (v.status === 'CLOSED') {
      if (S.es) { S.es.close(); S.es = null; }
      S.token = null;
      save();
    }
    render();
  }

  /* ---------- Helpers ---------- */

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function player(id) {
    var g = S.view && S.view.game;
    if (!g) return null;
    for (var i = 0; i < g.players.length; i++) if (g.players[i].id === id) return g.players[i];
    return null;
  }

  function nameOf(id) { var p = player(id); return p ? p.name : '?'; }

  function names(ids) {
    var list = ids.map(nameOf);
    if (list.length <= 1) return list.join('');
    return list.slice(0, -1).join(', ') + ' and ' + list[list.length - 1];
  }

  function clock(seconds) {
    var s = Math.max(0, seconds);
    return Math.floor(s / 60) + ':' + ('0' + (s % 60)).slice(-2);
  }

  function hostNow() { return Date.now() + S.offset; }

  var ROLE = {
    MAFIA: { title: 'Mafia', word: 'Mafia', art: 'outlaw', cls: 'mafia',
      head: 'WANTED', tag: 'DEAD OR ALIVE · $5,000 REWARD', blurb: 'Each night, pick a target with your gang. By day, look innocent.',
      prompt: 'Who gets it<br>tonight?' },
    ANGEL: { title: 'Angel', word: 'the Angel', art: 'angel', cls: 'angel',
      head: 'GUARDIAN', tag: 'WATCHES OVER THE TOWN', blurb: 'Each night, protect one person. If the Mafia come for them, they live.',
      prompt: 'Who will you<br>protect tonight?' },
    SHERIFF: { title: 'Sheriff', word: 'the Sheriff', art: 'sheriff', cls: 'sheriff',
      head: 'THE LAW', tag: 'BY ORDER OF THE TOWN', blurb: "Each night, ask about one person and learn if they're Mafia. Use it wisely by day.",
      prompt: 'Who will you<br>look into?' },
    VILLAGER: { title: 'Villager', word: 'a villager', art: 'villager', cls: 'villager',
      head: 'TOWNSFOLK', tag: 'HONEST CITIZEN', blurb: "Sniff out the Mafia and vote them out of town. At night, guess who they'll go after.",
      prompt: 'Who will the<br>Mafia go after?' },
  };

  function fingerprint() {
    var d = '';
    for (var i = 0; i <= 4; i++) {
      var rx = 44 * (0.07 + 0.085 * i), ry = rx * 1.22, cx = 22, cy = 23;
      var a1 = (115 + i * 9) * Math.PI / 180, sweep = 310 - i * 28, a2 = a1 + sweep * Math.PI / 180;
      var x1 = cx + rx * Math.cos(a1), y1 = cy + ry * Math.sin(a1);
      var x2 = cx + rx * Math.cos(a2), y2 = cy + ry * Math.sin(a2);
      d += 'M' + x1.toFixed(1) + ' ' + y1.toFixed(1) + 'A' + rx.toFixed(1) + ' ' + ry.toFixed(1) + ' 0 ' + (sweep > 180 ? 1 : 0) + ' 1 ' + x2.toFixed(1) + ' ' + y2.toFixed(1);
    }
    return '<svg viewBox="0 0 44 44" aria-hidden="true"><path d="' + d + '" fill="none" stroke="#1D1410" stroke-width="2.6" stroke-linecap="round"/></svg>';
  }
  var PRINT = fingerprint();

  /* ---------- Pieces ---------- */

  function ribbon(text) { return '<div class="ribbon">' + esc(text) + '</div>'; }

  function errorLine() { return S.error ? '<p class="error" role="alert">' + esc(S.error) + '</p>' : ''; }

  function thumb(key, label, hint) {
    return '<div class="thumb">' +
      '<button type="button" class="pad' + (S.hold === key ? ' on' : '') + '" data-hold="' + key + '" aria-label="' + esc(label + '. ' + hint) + '">' + PRINT + '</button>' +
      '<div><div class="label-big">' + esc(label) + '</div><div class="muted small">' + esc(hint) + '</div></div></div>';
  }

  function peekButton() {
    var g = S.view.game;
    if (!g || !g.me) return '';
    return '<button type="button" class="peek" data-hold="peek" aria-label="Hold to see your card">' + PRINT + '<span>My card</span></button>';
  }

  function poster(me) {
    var r = ROLE[me.role];
    var gang = '';
    if (me.role === 'MAFIA') {
      gang = me.gang.length ? '<span class="stamp">Your gang: ' + esc(me.gang.map(nameOf).join(' · ')) + '</span>' : '<span class="stamp">You ride alone</span>';
    }
    return '<div class="paper-panel">' +
      '<div class="poster-head">' + r.head + '</div>' +
      '<div class="poster-tag">' + esc(r.tag) + '</div>' +
      '<div class="poster-art"><img src="/art/' + r.art + '.png" alt="' + r.title + ' character"></div>' +
      '<div class="poster-role">' + r.title.toUpperCase() + '</div>' +
      '<div class="poster-blurb">' + esc(r.blurb) + '</div>' + gang + '</div>';
  }

  function cardBack() {
    return '<div class="paper-panel back"><div class="huge">?</div><div class="strong">Your card is face down</div></div>';
  }

  function peekOverlay() {
    var g = S.view.game;
    if (S.hold !== 'peek' || !g || !g.me) return '';
    return '<div class="overlay" role="dialog" aria-label="Your card">' + poster(g.me) + '</div>';
  }

  /** A name plank. opts: sel, disabled, dead, tag, leftTag */
  function plank(p, opts, act) {
    var cls = 'plank' + (opts.sel ? ' sel' : '') + (opts.dead ? ' dead' : '') + (p.name.length > 11 ? ' long' : p.name.length > 8 ? ' mid' : '');
    var tag = opts.tag && !opts.dead ? '<span class="tag">' + esc(opts.tag) + '</span>' : '';
    var left = opts.leftTag && !opts.dead ? '<span class="tag left">' + esc(opts.leftTag) + '</span>' : '';
    var label = p.name + (opts.dead ? ', out of the game' : opts.sel ? ', selected' : '');
    return '<div class="plank-wrap">' + left + tag + '<button type="button" class="' + cls + '" data-act="' + act + '" data-id="' + p.id + '"' +
      (opts.disabled || opts.dead ? ' disabled' : '') + ' aria-pressed="' + (opts.sel ? 'true' : 'false') + '" aria-label="' + esc(label) + '">' + esc(p.name) + '</button></div>';
  }

  function seatList(v) {
    return '<ul class="list">' + v.seats.map(function (s) {
      var you = v.you && v.you.id === s.id;
      return '<li><span class="dot' + (s.connected ? '' : ' off') + '" aria-hidden="true"></span>' +
        '<span class="grow strong">' + esc(s.name) + '</span>' +
        (s.host ? '<span class="chip">Host</span>' : '') + (you ? '<span class="chip">You</span>' : '') +
        (s.connected ? '' : '<span class="small muted">away</span>') + '</li>';
    }).join('') + '</ul>';
  }

  function suggestedMafia(n) { return n <= 5 ? 1 : n <= 8 ? 2 : 3; }

  function rulesNote(v) {
    var r = v.rules, n = v.seats.length;
    var mafia = r.mafiaCount || suggestedMafia(Math.max(5, n));
    var lines = [];
    if (n >= 5) lines.push(mafia + ' Mafia, 1 Angel, 1 Sheriff and ' + Math.max(0, n - mafia - 2) + ' villagers.');
    lines.push('Nights last up to ' + clock(r.nightSeconds) + '. They end sooner once everyone has chosen.');
    lines.push(r.voteMode === 'OPEN' ? 'Votes are shown with names.' : 'Votes are secret: only the count is shown.');
    lines.push(r.revealRoleOnDeath ? "A player's role is revealed when they're out." : 'Roles stay hidden until the end.');
    if (!r.angelCanSelfProtect) lines.push("The Angel can't protect themselves.");
    return '<div class="note flat"><h2 class="title">House rules</h2>' + lines.map(function (l) { return '<p class="small">' + esc(l) + '</p>'; }).join('') + '</div>';
  }

  function aliveLine(g) {
    var left = g.players.filter(function (p) { return p.alive; }).length;
    return left + ' left in town.';
  }

  /* ---------- Screens. Each returns [background, html]. ---------- */

  function joinScreen() {
    var h = S.hello;
    var table = h ? h.table : 'the table';
    var android = /Android/i.test(navigator.userAgent);
    var links = '';
    if (android) {
      var intent = 'intent://join?host=' + encodeURIComponent(location.host) +
        '#Intent;scheme=angelsheriffmafia;package=com.angelsheriffmafia.app;S.browser_fallback_url=' + encodeURIComponent(location.href) + ';end';
      links += '<a class="link" href="' + esc(intent) + '">Have the app? Open this table in it</a>';
      if (h && h.apk) links += '<a class="link" href="/app.apk" download>Get the app from the host</a>';
    }
    var reach = S.helloFailed && !h
      ? '<p class="error">Can\'t reach the host. Join the same Wi-Fi or hotspot as the host\'s phone, then try again.</p>' +
        '<button type="button" class="btn teal" data-act="retry">Try again</button>'
      : '';
    return ['bg-paper', '<section class="screen">' +
      ribbon('Angel Sheriff Mafia') +
      '<div class="title-art" aria-hidden="true"><span class="t1">ANGEL</span><span class="t2">SHERIFF</span><span class="t3">MAFIA</span></div>' +
      '<h1 class="headline">Join ' + esc(table) + '</h1>' +
      '<p class="body muted">The game runs on the host\'s phone. Play from here, no app needed.</p>' +
      (S.gone ? '<p class="error">' + esc(S.gone) + '</p>' : '') + reach +
      '<form class="stack" data-form="join" novalidate>' +
      '<div class="field"><label for="name">Your name</label><input id="name" name="name" maxlength="16" autocomplete="nickname" autocapitalize="words" enterkeyhint="go" value="' + esc(S.name) + '"></div>' +
      '<div class="field"><label for="pron">Say it like (optional, for the narrator)</label><input id="pron" name="pron" maxlength="32" placeholder="e.g. Ee-shaan" value="' + esc(S.pron) + '"></div>' +
      errorLine() +
      '<button class="btn" type="submit"' + (S.busy ? ' disabled' : '') + '>Take a seat</button>' +
      '</form>' + links + '</section>'];
  }

  function loadingScreen() {
    return ['bg-paper', '<section class="screen center"><p class="body">Finding your seat…</p></section>'];
  }

  function closedScreen(v) {
    return ['bg-paper', '<section class="screen center">' + ribbon(v.table) +
      '<h1 class="headline">The table is closed</h1><p class="body">Thanks for playing! When the host opens a new table, join it here.</p>' +
      '<button type="button" class="btn" data-act="rejoin">Join again</button></section>'];
  }

  function lobbyScreen(v) {
    var n = v.seats.length;
    var wait = n < 5 ? ' At least 5 are needed, ' + n + ' so far.' : ' ' + n + ' at the table.';
    return ['bg-day', '<section class="screen">' + ribbon(v.table) +
      '<h1 class="headline">You\'re in, ' + esc(v.you.name) + '!</h1>' +
      '<p class="body">Waiting for the host to deal the cards.' + esc(wait) + '</p>' +
      '<div class="note flat">' + seatList(v) + '</div>' + rulesNote(v) +
      '<p class="small">Once the cards are dealt, keep your screen to yourself.</p>' +
      errorLine() +
      '<button type="button" class="link" data-act="leave">Leave the table</button></section>'];
  }

  function stageWord(g) {
    switch (g.stage) {
      case 'DEAL': return 'The cards are being dealt.';
      case 'NIGHT': return 'Night ' + g.day + '. The town sleeps.';
      case 'DAWN': return 'Morning after night ' + g.day + '.';
      case 'DISCUSSION': return 'Day ' + g.day + ': the town is talking.';
      case 'VOTE': return 'Day ' + g.day + ': the town is voting.';
      case 'RESULT': return 'Day ' + g.day + ': the verdict is in.';
      default: return 'The game is over.';
    }
  }

  function watchingScreen(v) {
    var g = v.game;
    return ['bg-day', '<section class="screen">' + ribbon(v.table) +
      '<h1 class="headline">' + (g.stage === 'OVER' ? 'Pull up a chair' : 'A game is under way') + '</h1>' +
      '<p class="body">You\'ll be dealt in when the host starts the next one.</p>' +
      '<p class="strong">' + esc(stageWord(g)) + '</p>' +
      '<div class="note flat">' + seatList(v) + '</div></section>'];
  }

  function dealScreen(v, g) {
    var me = g.me;
    var seen = g.deal.seen;
    var holding = S.hold === 'card';
    var bottom;
    if (seen) {
      var w = g.deal.waiting;
      bottom = '<p class="body" style="text-align:center">' + (w.length ? 'Waiting for ' + esc(names(w)) + ' to check their card.' : 'Night is falling…') + '</p>';
    } else {
      bottom = '<button type="button" class="btn" data-act="seen"' + (S.seenCard && !holding && !S.busy ? '' : ' disabled') + '>Got it</button>';
    }
    return ['bg-wood', '<section class="screen">' + ribbon('For your eyes only') +
      '<h1 class="headline small">' + esc(nameOf(me.id)) + ', you are…</h1>' +
      '<div class="card-area">' + (holding ? poster(me) : cardBack()) + '</div>' +
      thumb('card', 'Hold to see your card', 'Let go and it hides again.' + (seen ? '' : ' Then tap Got it.')) +
      errorLine() + bottom + '</section>'];
  }

  function header(left, right) {
    return '<div class="row between">' + ribbon(left) + (right || '') + '</div>';
  }

  function nightClock(night) {
    return night.endsAt ? '<span class="clock-pill" data-ends="' + night.endsAt + '" aria-label="Time left tonight"></span>' : '';
  }

  function nightScreen(v, g) {
    var me = g.me, night = g.night;
    if (!me || !me.alive) {
      return ['bg-night', '<section class="screen">' + header('Night ' + g.day, nightClock(night)) +
        '<h1 class="headline">The town sleeps</h1>' +
        '<p class="body muted">' + (me ? "You're out of the game. Rest easy at Boot Hill, and keep what you know to yourself." : 'Wait for the morning.') + '</p>' +
        '<div class="grow"></div>' + peekOverlay() + '</section>'];
    }
    if (!night.canPick) return lockedScreen(g);
    var r = ROLE[me.role];
    var shown = S.sel != null ? S.sel : night.myPick;
    var gangTags = {};
    night.gangPicks.forEach(function (c) { (gangTags[c.target] = gangTags[c.target] || []).push(nameOf(c.by)); });
    var grid = g.players.map(function (p) {
      var tag = null;
      if (me.gang.indexOf(p.id) >= 0) tag = 'Partner';
      else if (p.id === me.id) tag = 'You';
      var picks = gangTags[p.id];
      var leftTag = picks ? (picks.length === 1 ? picks[0] + "'s pick" : picks.length + ' picks') : null;
      return plank(p, { sel: shown === p.id, disabled: night.targets.indexOf(p.id) < 0, dead: !p.alive, tag: tag, leftTag: leftTag }, 'sel');
    }).join('');

    var label, enabled = false;
    if (S.sel == null && night.myPick != null) {
      label = 'Waiting for your gang';
    } else if (shown == null) {
      label = 'Pick a name';
    } else {
      label = (night.myPick != null ? 'Switch to ' : 'Confirm ') + nameOf(shown);
      enabled = !S.busy;
    }
    return ['bg-night', '<section class="screen">' + header('Night ' + g.day, nightClock(night)) +
      '<h1 class="headline">' + r.prompt + '</h1>' +
      '<p class="body muted">' + esc(nameOf(me.id)) + ', you\'re ' + esc(r.word === 'Mafia' ? 'Mafia' : r.word) + '. Pick one name.</p>' +
      '<div class="planks grow">' + grid + '</div>' +
      '<div class="note"><span class="stamp">' + (me.role === 'MAFIA' && night.gangPicks.length ? 'Wire' : 'Note') + '</span><span>' + nightNote(me, night) + '</span></div>' +
      errorLine() +
      '<button type="button" class="btn' + (label.length > 18 ? ' long' : '') + '" data-act="pick"' + (enabled ? '' : ' disabled') + '>' + esc(label) + '</button>' +
      '</section>'];
  }

  function nightNote(me, night) {
    switch (me.role) {
      case 'MAFIA': {
        if (!me.gang.some(function (id) { var p = player(id); return p && p.alive; })) {
          return 'You ride alone tonight. Your choice is final.';
        }
        var parts = night.gangPicks.map(function (c) { return esc(nameOf(c.by)) + ' chose <b>' + esc(nameOf(c.target).toUpperCase()) + '</b>.'; });
        if (night.myPick != null) parts.push('You chose <b>' + esc(nameOf(night.myPick).toUpperCase()) + '</b>.');
        if (!parts.length) parts.push('Pick first, and your gang will see it.');
        parts.push('Pick the same name to agree.');
        var lead = night.lead === me.id ? 'yours' : esc(nameOf(night.lead)) + "'s";
        parts.push('If time runs out first, the most picks win, and on a split ' + lead + ' counts.');
        return parts.join(' ');
      }
      case 'ANGEL':
        return 'Whoever you pick survives a Mafia attack tonight.' +
          (S.view.rules.angelCanRepeatProtect ? '' : " You can't pick the same person two nights running.");
      case 'SHERIFF':
        return "You'll see what you learned right after you confirm. Only you.";
      default:
        return "Just a guess, it doesn't change the game. Right guesses score in the replay at the end.";
    }
  }

  function lockedScreen(g) {
    var me = g.me, night = g.night;
    var panel;
    if (S.hold === 'result') {
      var inner;
      if (night.sheriff) {
        var c = night.sheriff;
        var verdict = c.role ? 'is the ' + ROLE[c.role].title : c.isMafia ? 'is Mafia' : 'is not Mafia';
        inner = '<div class="label" style="color:#6B4A2A">Sheriff\'s report</div>' +
          '<div class="poster-head">' + esc(nameOf(c.target).toUpperCase()) + '</div>' +
          '<span class="stamp' + (c.isMafia ? '' : ' ink') + '">' + esc(verdict) + '</span>';
      } else {
        var what = me.role === 'MAFIA' ? 'Your gang is going after <b>' + esc(nameOf(night.myPick)) + '</b>.'
          : me.role === 'ANGEL' ? 'You\'re protecting <b>' + esc(nameOf(night.myPick)) + '</b>.'
          : 'You guessed <b>' + esc(nameOf(night.myPick)) + '</b>.';
        inner = '<div class="poster-head">LOCKED IN</div><p class="strong">' + what + ' Act natural.</p>';
      }
      panel = '<div class="paper-panel" role="status">' + inner + '</div>';
    } else {
      panel = '<span class="hidden-word">Hidden</span>';
    }
    return ['bg-night', '<section class="screen">' + header('Night ' + g.day, nightClock(night)) +
      '<h1 class="headline">Locked in.</h1>' +
      '<p class="body muted">Morning comes when everyone has chosen, or when the clock runs out. Hold the pad to check your result.</p>' +
      '<div class="card-area">' + panel + '</div>' +
      thumb('result', 'Hold to see', 'Let go and it hides again.') +
      '<p class="small muted" style="text-align:center">Keep your phone to yourself until morning.</p>' +
      '</section>'];
  }

  function dawnScreen(v, g) {
    var d = g.dawn;
    var line, art;
    if (d.killed != null) {
      var name = nameOf(d.killed);
      line = 'Last night the Mafia got ' + esc(name) + '.';
      art = '<div class="grave"><img src="/art/grave.png" alt="A wooden grave marker with a vulture perched on top">' +
        '<div class="on-marker"><div class="label" style="font-size:11px">R.I.P.</div><div class="name' + (name.length > 7 ? ' long' : '') + '">' + esc(name) + '</div>' +
        '<div class="small">Night ' + d.night + ' · Boot Hill</div></div>' +
        (d.killedRole ? '<span class="stamp">Was ' + esc(ROLE[d.killedRole].word) + '</span>' : '') + '</div>';
    } else {
      line = d.saved ? 'Someone was attacked in the night, but the Angel saved them!' : 'A quiet night. The Mafia didn\'t strike.';
      art = '<div class="sun"><img src="/art/rooster.png" alt="A rooster crowing on a fence post"></div>' +
        '<div style="text-align:center;margin-top:-10px"><span class="bubble">Cock-a-doodle-doo!</span></div>';
    }
    return ['bg-day', '<section class="screen">' + header('After night ' + d.night, peekButton()) +
      '<h1 class="headline">Morning in Dry Gulch!</h1>' +
      '<div class="grow" style="display:flex;flex-direction:column;justify-content:center">' + art + '</div>' +
      '<div class="foot"><p class="strong">' + line + '</p><p class="body">' + aliveLine(g) + '</p>' +
      '<p class="small">' + (g.decided ? 'The host shows who won.' : 'The host starts the talking.') + '</p></div>' + peekOverlay() + '</section>'];
  }

  function discussionScreen(v, g) {
    var d = g.discussion;
    var alive = g.players.filter(function (p) { return p.alive; });
    var face = d.endsAt ? '<div class="clock" data-ends="' + d.endsAt + '" role="timer" aria-label="Time left"></div>'
      : '<div class="clock">' + clock(d.seconds) + '</div>';
    return ['bg-day', '<section class="screen">' + header('Day ' + g.day + ' · Town meeting', peekButton()) +
      '<h1 class="headline">Who\'s the Mafia?</h1><p class="body">Talk it out. Accuse, defend, bluff.</p>' +
      '<div class="grow" style="display:flex;align-items:center">' + face + '</div>' +
      '<div class="foot"><p class="body">' + alive.length + ' still in town: ' + esc(alive.map(function (p) { return p.name; }).join(', ')) + '</p>' +
      '<p class="small">' + (d.startedAt ? 'The host calls the vote when you\'re ready.' : 'Waiting for the host to start the clock.') + '</p></div>' +
      peekOverlay() + '</section>'];
  }

  function voteScreen(v, g) {
    var me = g.me, vote = g.vote;
    var head = (vote.revote ? 'Revote' : 'The vote') + ' · day ' + g.day;
    if (!me || !vote.canVote) {
      var mine = '';
      if (vote.voted) mine = vote.skipped ? 'You skipped.' : 'You voted for ' + esc(nameOf(vote.myVote)) + '.';
      var w = vote.waiting;
      return ['bg-day', '<section class="screen">' + header(head, peekButton()) +
        '<h1 class="headline">' + (vote.voted ? 'Your vote is in.' : 'The town is voting') + '</h1>' +
        (mine ? '<p class="strong">' + mine + '</p>' : '') +
        '<p class="body">' + (w.length ? 'Waiting for ' + esc(names(w)) + '.' : 'Counting…') + '</p>' +
        '<div class="grow"></div>' + peekOverlay() + '</section>'];
    }
    var grid = g.players.filter(function (p) { return vote.candidates.indexOf(p.id) >= 0 || !p.alive; }).map(function (p) {
      return plank(p, { sel: S.sel === p.id, disabled: vote.candidates.indexOf(p.id) < 0 || p.id === me.id, dead: !p.alive, tag: p.id === me.id ? 'You' : null }, 'sel');
    }).join('');
    var label = S.sel != null ? 'Vote ' + nameOf(S.sel) : 'Pick a name';
    var secret = v.rules.voteMode !== 'OPEN';
    return ['bg-night', '<section class="screen">' + header(head, peekButton()) +
      '<h1 class="headline">' + (vote.revote ? 'It\'s a tie. Vote again!' : 'Who should leave town?') + '</h1>' +
      '<p class="body muted">' + esc(nameOf(me.id)) + ', pick one name. ' + (secret ? 'Only the count will be shown.' : 'Everyone will see who voted for whom.') + '</p>' +
      '<div class="planks grow">' + grid + '</div>' + errorLine() +
      (v.rules.allowSkipVote ? '<button type="button" class="link" data-act="skip"' + (S.busy ? ' disabled' : '') + '>Skip my vote</button>' : '') +
      '<button type="button" class="btn' + (label.length > 18 ? ' long' : '') + '" data-act="vote"' + (S.sel != null && !S.busy ? '' : ' disabled') + '>' + esc(label) + '</button>' +
      peekOverlay() + '</section>'];
  }

  function resultScreen(v, g) {
    var r = g.result;
    var out = r.eliminated != null ? player(r.eliminated) : null;
    var figure;
    if (out) {
      figure = '<div class="figure"><div class="poster-text">' + esc(out.name) + '</div>' +
        (r.role ? '<span class="stamp">Was ' + esc(ROLE[r.role].word) + '</span>' : '') +
        '<img src="/art/' + (r.role ? ROLE[r.role].art : 'villager') + '.png" alt=""></div>';
    } else {
      figure = '<div class="figure"><img src="/art/villager.png" alt="A relieved villager"></div>';
    }
    var max = r.tally.reduce(function (m, t) { return Math.max(m, t.count); }, 1);
    var tally = r.tally.length || r.skips ? '<div class="note flat"><h2 class="title">The count</h2><ul class="list tally">' +
      r.tally.map(function (t) {
        return '<li><span class="grow strong">' + esc(nameOf(t.target)) + '</span><span class="bar" style="width:' + Math.round(80 * t.count / max) + 'px"></span><span class="count">' + t.count + '</span></li>';
      }).join('') + (r.skips ? '<li><span class="grow">Skipped</span><span class="count">' + r.skips + '</span></li>' : '') + '</ul>' +
      (r.ballots ? '<p class="small" style="margin-top:8px">' + esc(r.ballots.map(function (b) { return nameOf(b.by) + ' → ' + (b.target != null ? nameOf(b.target) : 'skip'); }).join(' · ')) + '</p>' : '') +
      '</div>' : '';
    var why = out && r.randomTieBreak ? 'A tie, settled by the luck of the draw.'
      : out ? 'The town has spoken.'
      : r.tied.length ? 'Still tied, so the town lets everyone stay.'
      : 'The town couldn\'t agree on anyone.';
    return ['bg-day', '<section class="screen">' + header('Day ' + g.day + ' · The verdict', peekButton()) +
      '<h1 class="headline">' + (out ? 'Run out of town!' : 'Nobody leaves today') + '</h1>' +
      figure + tally +
      '<div class="foot"><p class="strong">' + esc(why) + '</p><p class="body">' + aliveLine(g) + '</p><p class="small">' +
      (g.decided ? 'The host shows who won.' : 'The host starts the next night.') + '</p></div>' +
      peekOverlay() + '</section>'];
  }

  function overScreen(v, g) {
    var o = g.over;
    var town = o.winner === 'TOWN';
    var art = (town ? ['angel', 'sheriff', 'villager'] : ['outlaw']).map(function (a) { return '<img src="/art/' + a + '.png" alt="">'; }).join('');
    var who = g.players.map(function (p) {
      return '<li><span class="dot ' + ROLE[p.role].cls + '" aria-hidden="true"></span><span class="grow strong">' + esc(p.name) + '</span><span>' +
        ROLE[p.role].title + (p.alive ? '' : ' · out') + '</span></li>';
    }).join('');
    var story = o.story.map(function (l) {
      if (l.style === 'HEADING') return '<h3>' + esc(l.text) + '</h3>';
      return '<p' + (l.style === 'STRONG' ? ' class="strong"' : '') + '>' + esc(l.text) + '</p>';
    }).join('');
    var guessers = o.guessers.length ? '<div class="note flat story"><h2 class="title">Best guessers</h2>' + o.guessers.map(function (t) { return '<p>' + esc(t) + '</p>'; }).join('') + '</div>' : '';
    return ['bg-day', '<section class="screen">' + ribbon('Game over') +
      '<div class="win' + (town ? '' : ' mafia') + '" role="heading" aria-level="1">' + (town ? 'TOWN WINS' : 'MAFIA WIN') + '</div>' +
      '<p class="strong" style="text-align:center">' + (town ? 'Every Mafia member has been run out of Dry Gulch.' : 'The Mafia have taken over Dry Gulch.') + '</p>' +
      '<div class="lineup">' + art + '</div>' +
      '<div class="note flat"><h2 class="title">Who was who</h2><ul class="list">' + who + '</ul></div>' +
      '<div class="note flat story"><h2 class="title">The story</h2>' + story + '</div>' + guessers +
      '<p class="body" style="text-align:center">Waiting for the host to deal again.</p></section>'];
  }

  function screen() {
    if (!S.token) return joinScreen();
    var v = S.view;
    if (!v) return loadingScreen();
    if (v.status === 'CLOSED') return closedScreen(v);
    if (v.status === 'LOBBY' || !v.game) return lobbyScreen(v);
    if (v.status === 'WATCHING') return watchingScreen(v);
    var g = v.game;
    switch (g.stage) {
      case 'DEAL': return dealScreen(v, g);
      case 'NIGHT': return nightScreen(v, g);
      case 'DAWN': return dawnScreen(v, g);
      case 'DISCUSSION': return discussionScreen(v, g);
      case 'VOTE': return voteScreen(v, g);
      case 'RESULT': return resultScreen(v, g);
      default: return overScreen(v, g);
    }
  }

  /* ---------- Drawing ---------- */

  function render() {
    var out = screen();
    document.body.className = out[0];
    if (out[1] !== S.lastHtml) {
      var active = document.activeElement;
      var focusId = active && active.id;
      var caret = focusId && active.selectionStart != null ? [active.selectionStart, active.selectionEnd] : null;
      var scroller = document.scrollingElement || document.documentElement;
      var scrollTop = scroller.scrollTop;
      var sameScreen = S.lastHtml && out[1].slice(0, 120) === S.lastHtml.slice(0, 120);
      $app.innerHTML = out[1];
      S.lastHtml = out[1];
      if (sameScreen) scroller.scrollTop = scrollTop;
      if (focusId) {
        var el = document.getElementById(focusId);
        if (el) {
          el.focus({ preventScroll: true });
          if (caret && el.setSelectionRange) { try { el.setSelectionRange(caret[0], caret[1]); } catch (e) { /* ignore */ } }
        }
      }
    }
    tick();
    showBanner();
  }

  function showBanner() {
    var text = S.token && S.lost ? 'Lost touch with the host. Reconnecting…' : '';
    $banner.textContent = text;
    $banner.hidden = !text;
  }

  function tick() {
    var now = hostNow();
    var els = $app.querySelectorAll('[data-ends]');
    for (var i = 0; i < els.length; i++) {
      var left = Math.max(0, Math.ceil((+els[i].getAttribute('data-ends') - now) / 1000));
      var text = clock(left);
      if (els[i].textContent !== text) els[i].textContent = text;
      els[i].classList.toggle('out', left === 0);
    }
  }

  /* ---------- Input ---------- */

  $app.addEventListener('click', function (e) {
    var el = e.target.closest('[data-act]');
    if (!el || el.disabled) return;
    switch (el.getAttribute('data-act')) {
      case 'sel':
        S.sel = +el.getAttribute('data-id');
        S.error = null;
        render();
        break;
      case 'pick':
        if (S.sel != null) send({ type: 'pick', target: S.sel }).then(function (ok) { if (ok) { S.sel = null; render(); } });
        break;
      case 'vote':
        if (S.sel != null) send({ type: 'vote', target: S.sel });
        break;
      case 'skip':
        send({ type: 'vote', target: null });
        break;
      case 'seen':
        send({ type: 'seen' });
        break;
      case 'leave':
        if (window.confirm('Leave the table?')) {
          send({ type: 'leave' }).then(function (ok) {
            if (!ok) return;
            if (S.es) { S.es.close(); S.es = null; }
            S.token = null;
            S.view = null;
            save();
            render();
          });
        }
        break;
      case 'retry':
        hello().then(render);
        break;
      case 'rejoin':
        S.gone = null;
        S.view = null;
        hello().then(render);
        break;
    }
  });

  $app.addEventListener('submit', function (e) {
    e.preventDefault();
    join();
  });

  $app.addEventListener('input', function (e) {
    if (e.target.id === 'name') S.name = e.target.value;
    if (e.target.id === 'pron') S.pron = e.target.value;
  });

  // Hold to see: secrets show only while a finger (or the space bar) is down.
  $app.addEventListener('pointerdown', function (e) {
    var el = e.target.closest('[data-hold]');
    if (!el) return;
    e.preventDefault();
    hold(el.getAttribute('data-hold'));
  });
  $app.addEventListener('keydown', function (e) {
    var el = e.target.closest && e.target.closest('[data-hold]');
    if (!el || (e.key !== ' ' && e.key !== 'Enter') || e.repeat) return;
    e.preventDefault();
    hold(el.getAttribute('data-hold'));
  });
  $app.addEventListener('keyup', function (e) {
    if (e.key === ' ' || e.key === 'Enter') release();
  });
  $app.addEventListener('contextmenu', function (e) {
    if (e.target.closest('[data-hold]')) e.preventDefault();
  });
  window.addEventListener('pointerup', release);
  window.addEventListener('pointercancel', release);
  window.addEventListener('blur', release);

  function hold(key) {
    S.hold = key;
    if (key === 'card') S.seenCard = true;
    render();
  }

  function release() {
    if (!S.hold) return;
    S.hold = null;
    render();
  }

  document.addEventListener('visibilitychange', function () {
    if (document.visibilityState === 'hidden') { release(); return; }
    // Back from a locked screen: make sure the stream is alive.
    if (S.token && (!S.es || S.es.readyState === 2)) connect();
  });

  setInterval(tick, 250);

  /* ---------- Start ---------- */

  load();
  hello().then(function () {
    if (S.token) connect();
    render();
  });
  render();
})();
