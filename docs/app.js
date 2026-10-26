'use strict';

/* ------------------------------------------------------------------ utils */

const $ = (sel, root = document) => root.querySelector(sel);
const app = $('#app');

function el(tag, cls, text) {
  const node = document.createElement(tag);
  if (cls) node.className = cls;
  if (text !== undefined && text !== null) node.textContent = text;
  return node;
}

function fold(value) {
  return (value || '')
    .toLowerCase()
    .replace(/ё/g, 'е')
    .replace(/й/g, 'и')
    .replace(/[«»"'`.,]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
}

const CLUB_PREFIXES = ['гбоушиор', 'гбоу', 'гбу', 'сшор', 'сш', 'дюсшор', 'дюсш'];
function clubKey(name) {
  let s = fold(name);
  let changed = true;
  while (changed) {
    changed = false;
    for (const p of CLUB_PREFIXES) {
      if (s.startsWith(p + ' ')) { s = s.slice(p.length + 1).trim(); changed = true; }
    }
  }
  return s;
}

function personKey(name, year) {
  const tokens = fold(name).split(' ').filter(Boolean).sort().join(' ');
  return tokens + '|' + (year || 0);
}

function plural(n, one, few, many) {
  const abs = Math.abs(n) % 100;
  const last = abs % 10;
  if (abs > 10 && abs < 20) return many;
  if (last === 1) return one;
  if (last >= 2 && last <= 4) return few;
  return many;
}
const gamesWord = n => `${n} ${plural(n, 'игра', 'игры', 'игр')}`;
const teamsWord = n => `${n} ${plural(n, 'команда', 'команды', 'команд')}`;
const playersWord = n => `${n} ${plural(n, 'игрок', 'игрока', 'игроков')}`;
const venuesWord = n => `${n} ${plural(n, 'зал', 'зала', 'залов')}`;
const divisionsWord = n => `${n} ${plural(n, 'дивизион', 'дивизиона', 'дивизионов')}`;

const MONTHS = ['янв', 'фев', 'мар', 'апр', 'мая', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек'];
const WEEKDAYS = ['вс', 'пн', 'вт', 'ср', 'чт', 'пт', 'сб'];

function parseDate(iso) {
  if (!iso) return null;
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
}
function dateBadge(iso) {
  const d = parseDate(iso);
  if (!d) return { day: '—', month: 'нет даты' };
  return { day: String(d.getDate()).padStart(2, '0'), month: `${MONTHS[d.getMonth()]}, ${WEEKDAYS[d.getDay()]}` };
}
function fullDate(iso) {
  const d = parseDate(iso);
  if (!d) return 'дата не назначена';
  return `${String(d.getDate()).padStart(2, '0')}.${String(d.getMonth() + 1).padStart(2, '0')}.${d.getFullYear()}, ${WEEKDAYS[d.getDay()]}`;
}
function todayIso() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/* ------------------------------------------------------------------ state */

const S = {
  data: null,
  divisions: new Map(),
  teams: new Map(),
  persons: new Map(),       // personKey -> {key, name, birthYear, players:[], teams:[]}
  playerPerson: new Map(),  // playerId -> personKey
  favTeams: new Set(JSON.parse(localStorage.getItem('vp.favTeams') || '[]')),
  favPersons: new Set(JSON.parse(localStorage.getItem('vp.favPersons') || '[]')),
  splits: new Set(JSON.parse(localStorage.getItem('vp.splits') || '[]')),
  merges: new Map(Object.entries(JSON.parse(localStorage.getItem('vp.merges') || '{}'))),
  gender: 'all',
  year: 0,
  calMode: 'list',
  calMonth: null,
  calDay: null,
  calPast: false,
  homePast: false,
  favPast: false,
  calDivision: 0,
  searchQuery: '',
};

function saveFavorites() {
  localStorage.setItem('vp.favTeams', JSON.stringify([...S.favTeams]));
  localStorage.setItem('vp.favPersons', JSON.stringify([...S.favPersons]));
}

function isFavTeam(id) { return S.favTeams.has(id); }
function isFavPerson(key) { return S.favPersons.has(resolveKey(key)); }
function toggleFavTeam(id) {
  if (S.favTeams.has(id)) S.favTeams.delete(id); else S.favTeams.add(id);
  saveFavorites();
  render();
}
function toggleFavPerson(key) {
  const resolved = resolveKey(key);
  if (S.favPersons.has(resolved)) S.favPersons.delete(resolved); else S.favPersons.add(resolved);
  saveFavorites();
  render();
}

function trackedTeamIds() {
  const ids = new Set(S.favTeams);
  for (const key of S.favPersons) {
    const person = S.persons.get(resolveKey(key));
    if (person) for (const team of person.teams) ids.add(team.id);
  }
  return ids;
}

/* ------------------------------------------------------------------ data */

const UI_DEFAULTS = { gender: 'all', year: 0, calDivision: 0, calMode: 'list', calMonth: null, calDay: null };

// Переключение лент не должно уводить экран вверх: запоминаем положение вкладок.
let scrollAnchor = null;

function anchorTabs(node) {
  const list = [...document.querySelectorAll('.seghead')];
  const index = list.indexOf(node);
  if (index >= 0) scrollAnchor = { top: node.getBoundingClientRect().top, index };
}

function restoreScroll() {
  if (!scrollAnchor) {
    window.scrollTo(0, 0);
    return;
  }
  const { top, index } = scrollAnchor;
  scrollAnchor = null;
  const node = [...document.querySelectorAll('.seghead')][index];
  if (!node) {
    window.scrollTo(0, 0);
    return;
  }
  window.scrollBy(0, node.getBoundingClientRect().top - top);
}

function restoreUi() {
  let saved = {};
  try {
    saved = JSON.parse(localStorage.getItem('vp.ui') || '{}');
  } catch (error) {
    saved = {};
  }
  Object.assign(S, UI_DEFAULTS, saved);
}

function saveUi() {
  localStorage.setItem('vp.ui', JSON.stringify({
    gender: S.gender,
    year: S.year,
    calDivision: S.calDivision,
    calMode: S.calMode,
    calMonth: S.calMonth,
    calDay: S.calDay,
  }));
}

function resetUi() {
  Object.assign(S, UI_DEFAULTS);
  saveUi();
  render();
}

function saveOverrides() {
  localStorage.setItem('vp.splits', JSON.stringify([...S.splits]));
  localStorage.setItem('vp.merges', JSON.stringify(Object.fromEntries(S.merges)));
}

function resolveKey(key) {
  let current = key;
  const seen = new Set();
  while (S.merges.has(current) && !seen.has(current)) {
    seen.add(current);
    current = S.merges.get(current);
  }
  return current;
}

function effectiveKey(player) {
  if (S.splits.has(player.id)) return 'id:' + player.id;
  return resolveKey(personKey(player.name, player.birthYear));
}

function baseKey(player) {
  return personKey(player.name, player.birthYear);
}

function splitPlayer(player) {
  S.splits.add(player.id);
  saveOverrides();
  buildIndexes(S.data);
  render();
}

function mergePerson(fromKey, toKey) {
  const from = resolveKey(fromKey);
  const to = resolveKey(toKey);
  if (from === to) return;
  S.merges.set(from, to);
  saveOverrides();
  buildIndexes(S.data);
  render();
}

function resetOverrides() {
  S.splits.clear();
  S.merges.clear();
  saveOverrides();
  buildIndexes(S.data);
  render();
}

function normalizeDataset(data) {
  for (const division of data.divisions || []) division.id = String(division.id);
  for (const team of data.teams || []) {
    team.id = String(team.id);
    team.divisionId = String(team.divisionId);
  }
  for (const player of data.players || []) {
    player.id = String(player.id);
    player.teamId = String(player.teamId);
    player.divisionId = String(player.divisionId);
  }
  for (const game of data.games || []) {
    game.id = String(game.id);
    game.homeId = String(game.homeId);
    game.awayId = String(game.awayId);
    game.divisionId = String(game.divisionId);
  }
  return data;
}

function buildIndexes(data) {
  S.divisions.clear();
  S.teams.clear();
  S.persons.clear();
  S.playerPerson.clear();

  for (const d of data.divisions) S.divisions.set(d.id, d);
  for (const t of data.teams) S.teams.set(t.id, t);

  for (const p of data.players) {
    const key = effectiveKey(p);
    let person = S.persons.get(key);
    if (!person) {
      person = { key, name: p.name, birthYear: p.birthYear, photo: p.photo, players: [], teams: [] };
      S.persons.set(key, person);
    }
    if (!person.photo && p.photo) person.photo = p.photo;
    person.players.push(p);
    const team = S.teams.get(p.teamId);
    if (team && !person.teams.some(t => t.id === team.id)) person.teams.push(team);
  }
  for (const p of data.players) {
    S.playerPerson.set(p.id, effectiveKey(p));
  }
}

async function clearSiteCaches() {
  const controller = navigator.serviceWorker && navigator.serviceWorker.controller;
  if (controller) {
    controller.postMessage({ type: 'CLEAR' });
    return;
  }
  try {
    const keys = await caches.keys();
    await Promise.all(keys.map(key => caches.delete(key)));
  } catch (error) {
    // кэш может быть недоступен — не мешаем обновлению
  }
}

async function refreshAll(button) {
  const label = button.textContent;
  button.disabled = true;
  button.textContent = 'Обновляем…';
  try {
    await clearSiteCaches();
    await loadData(true);
    if ('serviceWorker' in navigator) {
      const registration = await navigator.serviceWorker.getRegistration();
      if (registration) {
        await registration.update();
        if (registration.waiting) registration.waiting.postMessage({ type: 'SKIP_WAITING' });
      }
    }
    render();
  } finally {
    button.disabled = false;
    button.textContent = label;
  }
}

async function loadData(fresh) {
  const url = fresh ? `data.json?ts=${Date.now()}` : 'data.json';
  try {
    const response = await fetch(url, { cache: 'no-store' });
    if (!response.ok) throw new Error('HTTP ' + response.status);
    S.data = normalizeDataset(await response.json());
    buildIndexes(S.data);
    localStorage.setItem('vp.lastSync', new Date().toISOString());
    localStorage.setItem('vp.data', JSON.stringify({ at: Date.now(), data: S.data }));
  } catch (error) {
    const cached = localStorage.getItem('vp.data');
    if (cached) {
      S.data = normalizeDataset(JSON.parse(cached).data);
      buildIndexes(S.data);
    } else {
      app.innerHTML = '<div class="empty">Не удалось загрузить данные. Проверьте интернет и обновите страницу.</div>';
      return;
    }
  }
  document.addEventListener('online', () => { updateOnline(); loadData(true); });
  document.addEventListener('offline', updateOnline);
  updateOnline();
  render();
}

let onlineTimer = null;

function updateOnline() {
  const bar = $('#offline');
  const online = navigator.onLine;
  if (bar) bar.hidden = online;

  if (online) {
    if (onlineTimer) {
      clearInterval(onlineTimer);
      onlineTimer = null;
    }
    return;
  }

  // пока сети нет — проверяем её появление сами
  if (!onlineTimer) {
    onlineTimer = setInterval(() => {
      if (navigator.onLine) {
        updateOnline();
        loadData(true);
      }
    }, 4000);
  }
}

/* ------------------------------------------------------------------ games */

function gamesOfDivision(divisionId) {
  return S.data.games.filter(g => g.divisionId === divisionId);
}

function isChampionship(division) {
  return !!division && division.competition.toLowerCase().includes('чемпионат');
}

const ICON_PATHS = {
  home: '<path d="M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-5v-6H10v6H5a1 1 0 0 1-1-1Z" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>',
  calendar: '<rect x="3.5" y="5" width="17" height="15.5" rx="2.5" fill="none" stroke="currentColor" stroke-width="1.9"/><path d="M3.5 9.5h17M8 3.5V7M16 3.5V7" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>',
  list: '<path d="M8 6.5h12M8 12h12M8 17.5h9" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/><circle cx="4.2" cy="6.5" r="1.3" fill="currentColor"/><circle cx="4.2" cy="12" r="1.3" fill="currentColor"/><circle cx="4.2" cy="17.5" r="1.3" fill="currentColor"/>',
  pin: '<path d="M12 21s7-6.1 7-11a7 7 0 1 0-14 0c0 4.9 7 11 7 11Z" fill="none" stroke="currentColor" stroke-width="1.8"/><circle cx="12" cy="10" r="2.6" fill="none" stroke="currentColor" stroke-width="1.8"/>',
  star: '<path d="m12 3.6 2.6 5.4 5.9.8-4.3 4.1 1 5.9-5.2-2.8-5.2 2.8 1-5.9L3.5 9.8l5.9-.8Z" fill="currentColor"/>',
  starOutline: '<path d="m12 3.6 2.6 5.4 5.9.8-4.3 4.1 1 5.9-5.2-2.8-5.2 2.8 1-5.9L3.5 9.8l5.9-.8Z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>',
  close: '<path d="M6 6l12 12M18 6 6 18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>',
  whistle: '<path d="M4 10h9l3-3h4v5l-4 2H9a5 5 0 1 1-5-4Z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>',
  alert: '<circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M12 7.5v5.5" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/><circle cx="12" cy="16.6" r="1.1" fill="currentColor"/>',
};

function icon(name, cls) {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('class', cls || 'ico');
  svg.innerHTML = ICON_PATHS[name] || '';
  return svg;
}

function starButton(isOn, onToggle) {
  const button = el('button', 'star');
  button.append(icon(isOn ? 'star' : 'starOutline'));
  button.addEventListener('click', onToggle);
  return button;
}

function iconButton(name, onTap) {
  const button = el('button', 'toolbtn');
  button.append(icon(name));
  button.addEventListener('click', onTap);
  return button;
}

function monthKey(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

const GENDER_COLORS = { boys: '#1b4f9c', girls: '#c2417f', men: '#2f7d4f', women: '#e8862e' };

function genderColor(division) {
  return GENDER_COLORS[division ? division.gender : ''] || '#1b4f9c';
}

function compareDivisions(a, b) {
  const rank = division => (division.competition.toLowerCase().includes('первенство') ? 0 : 1);
  return rank(a) - rank(b)
    || (a.birthYear || 0) - (b.birthYear || 0)
    || (a.teamLabel || 0) - (b.teamLabel || 0);
}

function miniLogo(team) {
  if (team && team.logo) {
    const img = el('img', 'logo mini');
    img.alt = '';
    img.loading = 'lazy';
    img.referrerPolicy = 'no-referrer';
    img.addEventListener('error', () => img.remove());
    img.src = team.logo;
    return img;
  }
  return el('span', 'logo mini');
}

function teamNameCell(team, label) {
  const cell = el('div', 'namecell');
  cell.append(miniLogo(team));
  cell.append(el('span', 'ellipsis', label));
  return cell;
}

function chevron() {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('class', 'chev');
  svg.innerHTML = '<path d="M9 5l7 7-7 7" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>';
  return svg;
}

const GENDER_RU = { boys: 'Юноши', girls: 'Девушки', men: 'Мужчины', women: 'Женщины' };

function divisionSubtitle(division) {
  if (!division) return '';
  const gender = GENDER_RU[division.gender] || '';
  if (division.birthYear) return `${gender} · ${division.birthYear} г.р.`;
  if (division.teamLabel) return `${gender} · ${division.teamLabel}-я команда`;
  return gender;
}

function gameSets(game) {
  return (game.sets || []).filter(s => s.length > 1);
}

function homeSets(game) { return gameSets(game).filter(s => s[0] > s[1]).length; }
function awaySets(game) { return gameSets(game).filter(s => s[1] > s[0]).length; }

function sortGames(a, b) {
  const ad = a.date || '9999-12-31', bd = b.date || '9999-12-31';
  if (ad !== bd) return ad < bd ? -1 : 1;
  if ((a.time || '') !== (b.time || '')) return (a.time || '') < (b.time || '') ? -1 : 1;
  return 0;
}

function gameCard(game, options = {}) {
  const division = S.divisions.get(game.divisionId);
  const card = el('div', 'game');
  const comp = el('div', 'comp');
  const dot = el('span', 'dot');
  dot.style.background = genderColor(division);
  comp.append(dot, document.createTextNode(division ? `${division.competition} · ${divisionSubtitle(division)}` : ''));
  card.append(comp);

  const line = el('div', 'line');
  if (!options.hideDate) {
    const badge = el('div', 'date-badge');
    const b = dateBadge(game.date);
    badge.append(el('b', null, b.day), el('span', null, b.month));
    line.append(badge);
  }

  const teams = el('div', 'teams');
  for (const side of ['home', 'away']) {
    const row = el('div', 'team');
    const logo = S.teams.get(side === 'home' ? game.homeId : game.awayId);
    if (logo && logo.logo) {
      const img = el('img');
      img.alt = '';
      img.loading = 'lazy';
      img.referrerPolicy = 'no-referrer';
      img.src = logo.logo;
      img.addEventListener('error', () => img.remove());
      row.append(img);
    }
    row.append(el('span', null, side === 'home' ? game.home : game.away));
    teams.append(row);
  }
  line.append(teams);

  const score = el('div', 'badge');
  if (game.score) { score.textContent = game.score; score.classList.add('live'); }
  else score.textContent = game.time || '—';
  line.append(score);
  card.append(line);

  if (game.venue) card.append(el('div', 'venue', game.venue));
  if (game.corrected && !isPastGame(game)) {
    const warn = el('div', 'gamewarn');
    warn.append(icon('alert'), el('span', null, 'Внимание данная игра была скорректирована.'));
    card.append(warn);
  }
  if (options.showRefs && ((game.refs || []).length || game.comment)) {
    const extra = el('div', 'venue');
    if ((game.refs || []).length) extra.textContent = 'Судьи: ' + game.refs.join(', ');
    if (game.comment) extra.textContent += (extra.textContent ? ' · ' : '') + game.comment;
    card.append(extra);
  }

  card.addEventListener('click', () => { location.hash = `#/game/${game.divisionId}/${game.id}`; });
  return card;
}

/* ------------------------------------------------------------------ standings */

function ratioValue(num, den) {
  if (den === 0) return num === 0 ? 0 : Infinity;
  return num / den;
}

function ratioCompare(aNum, aDen, bNum, bDen) {
  const a = ratioValue(aNum, aDen);
  const b = ratioValue(bNum, bDen);
  if (a === b) return 0;
  return a > b ? 1 : -1;
}

function awardPoints(row, won, lost, firstTeam, championship) {
  const win = won > lost;
  if (win) row.wins++;
  else if (lost > won) row.losses++;
  if (championship) {
    if (win) row.points += (won === 3 && lost === 2) ? (firstTeam ? 4 : 2) : (firstTeam ? 6 : 3);
    else if (won === 2 && lost === 3) row.points += firstTeam ? 2 : 1;
  } else {
    if (win) row.points += (won === 3 && lost === 2) ? 2 : 3;
    else if (won === 2 && lost === 3) row.points += 1;
  }
}

function standings(divisionId) {
  const division = S.divisions.get(divisionId);
  if (!division) return [];
  const championship = isChampionship(division);
  const rows = new Map();
  for (const team of S.data.teams.filter(t => t.divisionId === divisionId)) {
    rows.set(team.id, { team, played: 0, wins: 0, losses: 0, points: 0, sf: 0, sa: 0, bf: 0, ba: 0 });
  }
  const played = S.data.games.filter(g => g.divisionId === divisionId && g.score && gameSets(g).length);
  const playedPairs = [];
  for (const game of played) {
    const home = rows.get(game.homeId), away = rows.get(game.awayId);
    if (!home || !away) continue;
    const hs = homeSets(game), as = awaySets(game);
    home.played++; away.played++;
    home.sf += hs; home.sa += as; away.sf += as; away.sa += hs;
    for (const set of gameSets(game)) {
      home.bf += set[0]; home.ba += set[1];
      away.bf += set[1]; away.ba += set[0];
    }
    const firstTeam = division.teamLabel === 1;
    awardPoints(home, hs, as, firstTeam, championship);
    awardPoints(away, as, hs, firstTeam, championship);
    playedPairs.push({ game, home, away, hs, as, firstTeam });
  }

  const list = [...rows.values()];
  list.sort((a, b) => {
    if (b.wins !== a.wins) return b.wins - a.wins;
    if (b.points !== a.points) return b.points - a.points;
    const sets = ratioCompare(a.sf, a.sa, b.sf, b.sa);
    if (sets !== 0) return -sets;
    const balls = ratioCompare(a.bf, a.ba, b.bf, b.ba);
    if (balls !== 0) return -balls;
    return a.team.name.localeCompare(b.team.name, 'ru');
  });

  // личные встречи внутри групп с равными победами и очками
  const result = [];
  let i = 0;
  while (i < list.length) {
    let j = i + 1;
    while (j < list.length && list[j].wins === list[i].wins && list[j].points === list[i].points) j++;
    const group = list.slice(i, j);
    if (group.length === 1) result.push(group[0]);
    else result.push(...sortByHeadToHead(group, playedPairs));
    i = j;
  }
  return result;
}

function sortByHeadToHead(group, playedPairs) {
  const names = new Set(group.map(r => r.team.name));
  const wins = new Map(), points = new Map();
  for (const entry of playedPairs) {
    if (!names.has(entry.home.team.name) || !names.has(entry.away.team.name)) continue;
    const homeWon = entry.hs > entry.as;
    const winner = homeWon ? entry.home : entry.away;
    const loser = homeWon ? entry.away : entry.home;
    const winnerSets = homeWon ? entry.hs : entry.as;
    const loserSets = homeWon ? entry.as : entry.hs;
    wins.set(winner.team.name, (wins.get(winner.team.name) || 0) + 1);
    const champ = winnerSets === 3 && loserSets === 2;
    const wp = champ ? (entry.firstTeam ? 4 : 2) : (entry.firstTeam ? 6 : 3);
    const lp = champ ? (entry.firstTeam ? 2 : 1) : 0;
    points.set(winner.team.name, (points.get(winner.team.name) || 0) + wp);
    points.set(loser.team.name, (points.get(loser.team.name) || 0) + lp);
  }
  return group.slice().sort((a, b) => {
    const hw = (wins.get(b.team.name) || 0) - (wins.get(a.team.name) || 0);
    if (hw !== 0) return hw;
    const hp = (points.get(b.team.name) || 0) - (points.get(a.team.name) || 0);
    if (hp !== 0) return hp;
    const sets = ratioCompare(a.sf, a.sa, b.sf, b.sa);
    if (sets !== 0) return -sets;
    return -ratioCompare(a.bf, a.ba, b.bf, b.ba);
  });
}

function combinedStandings(divisionId) {
  const division = S.divisions.get(divisionId);
  if (!division) return [];
  const pairIds = S.data.divisions
    .filter(d => d.competition === division.competition && d.gender === division.gender && d.birthYear == null)
    .map(d => d.id);
  if (!pairIds.length) return [];
  const clubs = new Map();
  for (const team of S.data.teams.filter(t => pairIds.includes(t.divisionId))) {
    const key = clubKey(team.name);
    if (!clubs.has(key)) clubs.set(key, { name: team.name, teams: [], played: 0, wins: 0, losses: 0, points: 0, sf: 0, sa: 0, bf: 0, ba: 0 });
    clubs.get(key).teams.push(team);
  }
  for (const game of S.data.games.filter(g => pairIds.includes(g.divisionId) && g.score && gameSets(g).length)) {
    const home = clubs.get(clubKey(game.home));
    const away = clubs.get(clubKey(game.away));
    if (!home || !away) continue;
    const hs = homeSets(game), as = awaySets(game);
    home.played++; away.played++;
    home.sf += hs; home.sa += as; away.sf += as; away.sa += hs;
    for (const set of gameSets(game)) {
      home.bf += set[0]; home.ba += set[1];
      away.bf += set[1]; away.ba += set[0];
    }
    const first = S.divisions.get(game.divisionId).teamLabel === 1;
    awardPoints(home, hs, as, first, true);
    awardPoints(away, as, hs, first, true);
  }
  return [...clubs.values()].sort((a, b) => {
    if (b.wins !== a.wins) return b.wins - a.wins;
    if (b.points !== a.points) return b.points - a.points;
    const sets = ratioCompare(a.sf, a.sa, b.sf, b.sa);
    if (sets !== 0) return -sets;
    return -ratioCompare(a.bf, a.ba, b.bf, b.ba);
  });
}

/* ------------------------------------------------------------------ views */

function gameStart(game) {
  if (!game || !game.date) return null;
  const time = /^\d{1,2}:\d{2}$/.test(game.time || '') ? game.time.padStart(5, '0') : '00:00';
  const start = new Date(`${game.date}T${time}:00`);
  return Number.isNaN(start.getTime()) ? null : start;
}

function isPastGame(game) {
  const start = gameStart(game);
  return start != null && Date.now() - start.getTime() >= 60 * 60 * 1000;
}

function screenHome() {
  const tracked = trackedTeamIds();
  const upcoming = S.data.games
    .filter(g => { const start = gameStart(g); return start != null && !isPastGame(g); })
    .sort(sortGames)
    .slice(0, 6);
  const pastGames = S.data.games
    .filter(isPastGame)
    .sort((a, b) => sortGames(b, a))
    .slice(0, 6);
  const myUpcoming = S.data.games
    .filter(g => (tracked.has(g.homeId) || tracked.has(g.awayId)) && gameStart(g) != null && !isPastGame(g))
    .sort(sortGames)
    .slice(0, 6);
  const myPast = S.data.games
    .filter(g => (tracked.has(g.homeId) || tracked.has(g.awayId)) && isPastGame(g))
    .sort((a, b) => sortGames(b, a))
    .slice(0, 6);
  const played = S.data.games.filter(g => g.score).length;
  const venues = new Set(S.data.games.map(g => g.venue).filter(Boolean));

  const wrap = document.createDocumentFragment();

  const stats = el('div', 'stats');
  const statItems = [
    [S.data.divisions.length, plural(S.data.divisions.length, 'дивизион', 'дивизиона', 'дивизионов')],
    [S.data.teams.length, plural(S.data.teams.length, 'команда', 'команды', 'команд')],
    [S.persons.size, plural(S.persons.size, 'игрок', 'игрока', 'игроков')],
    [played, 'сыграно'],
  ];
  for (const [value, label] of statItems) {
    const box = el('div', 'stat');
    box.append(el('b', null, String(value)), el('span', null, label));
    stats.append(box);
  }
  wrap.append(stats);

  const updated = el('div', 'card tight');
  const updatedRow = el('div', 'row');
  const updatedText = el('div', 'grow');
  updatedText.append(el('div', 'small muted', `Данные на ${shortMoment(S.data.generatedAt) || '—'}`));
  updatedText.append(el('div', 'strong', shortAge(S.data.generatedAt) || 'ещё не загружены'));
  updatedText.append(el('div', 'small muted', shortChecked()));
  updatedText.append(el('div', 'small muted', `Данные: ${sourceLabel()}`));
  updatedRow.append(updatedText);
  const refresh = el('button', 'btn btn-accent refresh', 'Обновить');
  refresh.addEventListener('click', () => refreshAll(refresh));
  updatedRow.append(refresh);
  updated.append(updatedRow);
  wrap.append(updated);

  const favTeamList = [...S.favTeams].map(id => S.teams.get(id)).filter(Boolean);
  if (favTeamList.length) {
    wrap.append(el('h2', 'section', 'Мои команды'));
    const card = el('div', 'card flush');
    for (const team of favTeamList) {
      const row = el('div', 'row pad');
      row.append(teamLogo(team));
      const text = el('div', 'grow');
      text.append(el('div', null, team.name));
      const division = S.divisions.get(team.divisionId);
      text.append(el('div', 'small muted ellipsis', division ? `${division.competition} · ${divisionSubtitle(division)}` : ''));
      row.append(text, chevron());
      row.addEventListener('click', () => { location.hash = `#/team/${team.id}`; });
      card.append(row);
    }
    wrap.append(card);
  }

  const favPersonList = [...S.favPersons].map(key => S.persons.get(resolveKey(key))).filter(Boolean);
  if (favPersonList.length) {
    wrap.append(el('h2', 'section', 'Мои игроки'));
    const card = el('div', 'card flush');
    for (const person of favPersonList) {
      const row = el('div', 'row pad');
      row.append(personAvatar(person));
      const text = el('div', 'grow');
      text.append(el('div', null, person.name));
      text.append(el('div', 'small muted', person.teams.length > 1 ? teamsWord(person.teams.length) : (person.teams[0] ? person.teams[0].name : '')));
      row.append(text, chevron());
      row.addEventListener('click', () => { location.hash = `#/player/${encodeURIComponent(person.key)}`; });
      card.append(row);
    }
    wrap.append(card);
  }

  if (tracked.size) {
    wrap.append(el('h2', 'section', 'Игры избранных'));
    const favPast = !!S.favPast;
    const favTabs = el('div', 'seghead');
    for (const [value, label] of [[false, 'Ближайшие'], [true, 'Прошедшие']]) {
      const button = el('button', (value === favPast ? 'on ' : '') + 'segbtn', label);
      button.addEventListener('click', () => {
        if (value !== favPast) { anchorTabs(favTabs); S.favPast = value; render(); }
      });
      favTabs.append(button);
    }
    wrap.append(favTabs);
    const favFeed = favPast ? myPast : myUpcoming;
    if (!favFeed.length) {
      wrap.append(el('div', 'empty', favPast ? 'Прошедших игр пока нет' : 'Ближайших игр пока нет'));
    }
    for (const game of favFeed) wrap.append(gameCard(game));
  }

  wrap.append(el('h2', 'section', 'Все игры'));
  const showPast = !!S.homePast;
  const segment = el('div', 'seghead');
  for (const [value, label] of [[false, 'Ближайшие'], [true, 'Прошедшие']]) {
    const button = el('button', (value === showPast ? 'on ' : '') + 'segbtn', label);
    button.addEventListener('click', () => {
      if (value !== showPast) { anchorTabs(segment); S.homePast = value; render(); }
    });
    segment.append(button);
  }
  wrap.append(segment);
  const feed = showPast ? pastGames : upcoming;
  if (!feed.length) {
    wrap.append(el('div', 'empty', showPast ? 'Прошедших игр пока нет' : 'Ближайших игр пока нет'));
  }
  for (const game of feed) wrap.append(gameCard(game));

  wrap.append(el('h2', 'section', 'Соревнования'));
  const competitions = el('div', 'stats');
  for (const [title, gender, note] of [['Первенство', 'boys', 'Юноши и девушки'], ['Чемпионат', 'men', 'Мужчины и женщины']]) {
    const count = S.data.divisions.filter(d => d.competition.includes(title)).length;
    const box = el('div', 'stat');
    const head = el('div', 'stathead');
    head.append(icon('calendar'), el('b', null, String(count)));
    box.append(head);
    box.append(el('span', null, plural(count, 'дивизион', 'дивизиона', 'дивизионов')));
    box.append(el('div', 'small muted', note));
    box.addEventListener('click', () => { S.gender = gender; location.hash = '#/divisions'; render(); });
    competitions.append(box);
  }
  wrap.append(competitions);

  wrap.append(el('h2', 'section', 'Залы'));
  const venueCard = el('div', 'card');
  const venueRow = el('div', 'row');
  const pin = el('div', 'roundicon');
  pin.append(icon('pin'));
  venueRow.append(pin);
  const venueText = el('div', 'grow');
  venueText.append(el('div', 'strong', 'Адреса игр'));
  venueText.append(el('div', 'small muted', venuesWord(venues.size) + ' · расписание по каждому'));
  venueRow.append(venueText, chevron());
  venueCard.append(venueRow);
  venueCard.addEventListener('click', () => { location.hash = '#/venues'; });
  wrap.append(venueCard);

  wrap.append(el('h2', 'section', 'Дивизионы'));
  const divisionsCard = el('div', 'card');
  const groups = [
    ['boys', 'Юноши'],
    ['girls', 'Девушки'],
    ['men', 'Мужчины'],
    ['women', 'Женщины'],
  ];
  for (const [gender, label] of groups) {
    const list = S.data.divisions
      .filter(d => d.gender === gender)
      .sort(compareDivisions);
    if (!list.length) continue;
    const block = el('div', 'divgroup');
    block.append(el('div', 'divlabel', label));
    const row = el('div', 'chips inner');
    for (const division of list) {
      const chip = el('button', 'chip', division.birthYear ? String(division.birthYear) : `${division.teamLabel}-я`);
      chip.addEventListener('click', () => { location.hash = `#/division/${division.id}`; });
      row.append(chip);
    }
    block.append(row);
    divisionsCard.append(block);
  }
  wrap.append(divisionsCard);

  return wrap;
}

function teamLogo(team) {
  if (team && team.logo) {
    const img = el('img', 'logo');
    img.alt = '';
    img.loading = 'lazy';
    img.referrerPolicy = 'no-referrer';
    img.src = team.logo;
    img.addEventListener('error', () => img.remove());
    return img;
  }
  return el('div', 'avatar');
}

function personAvatar(person) {
  if (person && person.photo) {
    const img = el('img', 'avatar');
    img.alt = '';
    img.loading = 'lazy';
    img.referrerPolicy = 'no-referrer';
    img.src = person.photo;
    img.addEventListener('error', () => img.remove());
    return img;
  }
  return el('div', 'avatar');
}

function screenDivisions() {
  const wrap = document.createDocumentFragment();
  const genders = [
    ['all', 'Все'], ['fav', 'Мои'], ['boys', 'Юноши'], ['girls', 'Девушки'], ['men', 'Мужчины'], ['women', 'Женщины'],
  ];
  const chips = el('div', 'chips');
  for (const [value, label] of genders) {
    const chip = el('button', 'chip' + (S.gender === value ? ' on' : ''), label);
    chip.addEventListener('click', () => { S.gender = value; S.year = 0; render(); });
    chips.append(chip);
  }
  wrap.append(chips);

  const tracked = trackedTeamIds();
  const favouriteDivisionIds = new Set(
    [...tracked].map(id => S.teams.get(id)).filter(Boolean).map(team => team.divisionId),
  );
  const list = S.data.divisions
    .filter(d => {
      if (S.gender === 'all') return true;
      if (S.gender === 'fav') return favouriteDivisionIds.has(d.id);
      return d.gender === S.gender;
    })
    .sort(compareDivisions);
  if (S.gender === 'fav' && !list.length) {
    wrap.append(el('div', 'empty', 'Пока ничего не выбрано. Добавьте команду или игрока звёздочкой — их дивизионы появятся здесь.'));
  }

  if (S.gender === 'boys' || S.gender === 'girls') {
    const years = [...new Set(list.map(d => d.birthYear))].filter(Boolean);
    const yearChips = el('div', 'chips');
    const all = el('button', 'chip alt' + (S.year === 0 ? ' on' : ''), 'Все годы');
    all.addEventListener('click', () => { S.year = 0; render(); });
    yearChips.append(all);
    for (const year of years) {
      const chip = el('button', 'chip alt' + (S.year === year ? ' on' : ''), String(year));
      chip.addEventListener('click', () => { S.year = year; render(); });
      yearChips.append(chip);
    }
    wrap.append(yearChips);
  }

  const filtered = list.filter(d => !S.year || d.birthYear === S.year);
  for (const division of filtered) {
    const teams = S.data.teams.filter(t => t.divisionId === division.id);
    const games = gamesOfDivision(division.id);
    const playedCount = games.filter(g => g.score).length;
    const accent = genderColor(division);

    const card = el('div', 'card divcard');
    const head = el('div', 'divhead');
    const titles = el('div', 'grow');
    titles.append(el('div', 'divtitle', divisionSubtitle(division)));
    titles.append(el('div', 'divcomp', division.competition));
    const chev = el('div', 'divchev', '›');
    chev.style.color = accent;
    head.append(titles, chev);
    card.append(head);

    const meta = el('div', 'divmeta');
    meta.append(el('span', null, teamsWord(teams.length)));
    meta.append(el('i', 'divdot', '·'));
    meta.append(el('span', null, `${playedCount} из ${gamesWord(games.length)}`));
    card.append(meta);

    const bar = el('div', 'bar');
    const fill = el('i');
    fill.style.width = games.length ? `${Math.round(playedCount / games.length * 100)}%` : '0%';
    fill.style.background = accent;
    bar.append(fill);
    card.append(bar);

    card.addEventListener('click', () => { location.hash = `#/division/${division.id}`; });
    wrap.append(card);
  }
  return wrap;
}

function screenDivision(divisionId, tab) {
  const division = S.divisions.get(divisionId);
  if (!division) return el('div', 'empty', 'Дивизион не найден');
  const wrap = document.createDocumentFragment();
  const current = tab || 'table';

  const tabs = [['table', 'Таблица'], ...(isChampionship(division) ? [['combined', 'Общая']] : []), ['games', 'Игры'], ['teams', 'Команды']];
  const chips = el('div', 'chips');
  for (const [value, label] of tabs) {
    const chip = el('button', 'chip' + (current === value ? ' on' : ''), label);
    chip.addEventListener('click', () => { location.hash = `#/division/${divisionId}/${value}`; });
    chips.append(chip);
  }
  wrap.append(chips);

  if (current === 'table') {
    const rows = standings(divisionId);
    const table = el('table', 'table');
    const head = el('tr');
    for (const h of ['#', 'Команда', 'И', 'В', 'П', 'Оч', 'Пар']) head.append(el('th', null, h));
    table.append(head);
    rows.forEach((row, index) => {
      const tr = el('tr');
      tr.append(el('td', null, String(index + 1)));
      const name = el('td', 'name');
      name.append(teamNameCell(row.team, row.team.name));
      name.addEventListener('click', () => { location.hash = `#/team/${row.team.id}`; });
      tr.append(name);
      tr.append(el('td', null, String(row.played)));
      tr.append(el('td', null, String(row.wins)));
      tr.append(el('td', null, String(row.losses)));
      tr.append(el('td', 'strong', String(row.points)));
      tr.append(el('td', null, `${row.sf}:${row.sa}`));
      table.append(tr);
    });
    const card = el('div', 'card flush');
    card.append(table);
    wrap.append(card);
    wrap.append(el('div', 'caption', isChampionship(division)
      ? 'Места: победы → очки → партии → мячи → личные встречи.\nОчки: у первой команды 6 за 3:0–3:1, 4 за 3:2, 2 за 2:3; у второй — 3, 2, 1.'
      : 'Места: победы → очки → партии → мячи → личные встречи.\nОчки: 3 за 3:0–3:1, 2 за 3:2, 1 за поражение 2:3.'));
  } else if (current === 'combined') {
    const rows = combinedStandings(divisionId);
    const table = el('table', 'table');
    const head = el('tr');
    for (const h of ['#', 'Коллектив', 'И', 'В', 'П', 'Оч', 'Пар']) head.append(el('th', null, h));
    table.append(head);
    rows.forEach((row, index) => {
      const tr = el('tr');
      tr.append(el('td', null, String(index + 1)));
      const clubCell = el('td', 'name');
      clubCell.append(teamNameCell(row.teams[0], row.name));
      tr.append(clubCell);
      tr.append(el('td', null, String(row.played)));
      tr.append(el('td', null, String(row.wins)));
      tr.append(el('td', null, String(row.losses)));
      tr.append(el('td', 'strong', String(row.points)));
      tr.append(el('td', null, `${row.sf}:${row.sa}`));
      table.append(tr);
    });
    const card = el('div', 'card flush');
    card.append(table);
    wrap.append(card);
    wrap.append(el('div', 'caption', 'Сумма показателей 1-й и 2-й команд коллектива.'));
  } else if (current === 'games') {
    const games = gamesOfDivision(divisionId).sort(sortGames);
    const dated = games.filter(g => g.date);
    const undated = games.filter(g => !g.date);
    if (!games.length) wrap.append(el('div', 'empty', 'Игр нет'));
    for (const game of dated) wrap.append(gameCard(game, { hideDate: false }));
    if (undated.length) {
      wrap.append(el('h2', 'section', `Дата не назначена · ${undated.length}`));
      for (const game of undated) wrap.append(gameCard(game));
    }
  } else {
    const teams = S.data.teams.filter(t => t.divisionId === divisionId);
    const card = el('div', 'card flush');
    for (const team of teams) {
      const row = el('div', 'row pad');
      row.append(teamLogo(team));
      const text = el('div', 'grow');
      text.append(el('div', null, team.name));
      text.append(el('div', 'small muted', playersWord(S.data.players.filter(p => p.teamId === team.id).length)));
      row.append(text, chevron());
      row.addEventListener('click', () => { location.hash = `#/team/${team.id}`; });
      card.append(row);
    }
    wrap.append(card);
  }
  return wrap;
}

function screenTeam(teamId) {
  const team = S.teams.get(teamId);
  if (!team) return el('div', 'empty', 'Команда не найдена');
  const division = S.divisions.get(team.divisionId);
  const wrap = document.createDocumentFragment();

  const profile = el('div', 'profile');
  profile.append(teamLogo(team));
  const info = el('div');
  info.append(el('h3', null, team.name));
  const divisionLine = el('div', 'muted', division ? `${division.competition} · ${divisionSubtitle(division)} ›` : '');
  if (division) {
    divisionLine.style.cursor = 'pointer';
    divisionLine.addEventListener('click', () => { location.hash = `#/division/${division.id}`; });
  }
  info.append(divisionLine);
  profile.append(info);
  profile.append(starButton(isFavTeam(team.id), () => toggleFavTeam(team.id)));
  wrap.append(profile);

  const players = S.data.players.filter(p => p.teamId === team.id);
  wrap.append(el('h2', 'section', `Состав · ${players.length}`));
  if (!players.length) {
    const card = el('div', 'card');
    card.append(el('div', 'strong', 'Заявка ещё не опубликована'));
    card.append(el('div', 'small muted', 'Федерация заполняет составы постепенно — они появляются на pitervolley.ru.'));
    wrap.append(card);
  }
  const grid = el('div', 'players');
  for (const player of players) {
    const key = S.playerPerson.get(player.id);
    const person = S.persons.get(key);
    const card = el('div', 'player');
    card.append(personAvatar(person || player));
    card.append(el('div', 'playername', player.name));
    if (player.birthYear) card.append(el('div', 'small muted', `${player.birthYear} г.р.`));
    card.addEventListener('click', () => { location.hash = `#/player/${encodeURIComponent(key)}/${player.id}`; });
    grid.append(card);
  }
  wrap.append(grid);

  const games = S.data.games
    .filter(g => g.homeId === team.id || g.awayId === team.id)
    .sort(sortGames);
  if (games.length) {
    wrap.append(el('h2', 'section', 'Игры команды'));
    for (const game of games) wrap.append(gameCard(game));
  }
  return wrap;
}

function screenPlayer(key, playerId) {
  const person = S.persons.get(key);
  if (!person) return el('div', 'empty', 'Игрок не найден');
  const player = playerId ? S.data.players.find(item => item.id === playerId) : null;
  const wrap = document.createDocumentFragment();

  const profile = el('div', 'profile');
  profile.append(personAvatar(person));
  const info = el('div');
  info.append(el('h3', null, person.name));
  if (person.birthYear) info.append(el('div', 'muted', `${person.birthYear} г.р.`));
  profile.append(info);
  profile.append(starButton(isFavPerson(key), () => toggleFavPerson(key)));
  wrap.append(profile);

  if (person.teams.length) {
    wrap.append(el('h2', 'section', person.teams.length > 1 ? `Заявлен за ${teamsWord(person.teams.length)}` : 'Команда'));
    const card = el('div', 'card flush');
    for (const team of person.teams) {
      const division = S.divisions.get(team.divisionId);
      const row = el('div', 'row pad');
      row.append(teamLogo(team));
      const text = el('div', 'grow');
      text.append(el('div', null, team.name));
      text.append(el('div', 'small muted', division ? `${division.competition} · ${divisionSubtitle(division)}` : ''));
      row.append(text, chevron());
      row.addEventListener('click', () => { location.hash = `#/team/${team.id}`; });
      card.append(row);
    }
    wrap.append(card);
  }

  const canSplit = player && person.players.length > 1 && !S.splits.has(player.id);
  const edited = person.key.startsWith('id:') || person.players.some(item =>
    S.splits.has(item.id) || S.merges.has(baseKey(item)) || person.key !== baseKey(item));
  {
    wrap.append(el('h2', 'section', 'Если это ошибка'));
    const tools = el('div', 'card');
    tools.append(el('div', 'small muted', 'Бывает, что одно и то же имя пишется по-разному или, наоборот, происходит объединение двух разных спортсменов. Здесь это можно исправить.'));
    const buttons = el('div', 'buttonrow');

    if (canSplit) {
      const split = el('button', 'btn btn-ghost', 'Это разные люди');
      split.addEventListener('click', () => {
        const target = 'id:' + player.id;
        splitPlayer(player);
        location.hash = `#/player/${encodeURIComponent(target)}/${player.id}`;
      });
      buttons.append(split);
    }

    const link = el('button', 'btn', 'Связать с игроком');
    link.addEventListener('click', () => openMergeDialog(resolveKey(key)));
    buttons.append(link);
    tools.append(buttons);
    wrap.append(tools);
  }

  if (edited) {
    const notice = el('div', 'notice');
    notice.append(el('div', null, 'Есть ручные правки группировки игроков. От этого зависят счётчик игроков, избранное и список игр.'));
    const reset = el('button', 'linkish', 'Сбросить все правки');
    reset.addEventListener('click', () => {
      // после сброса человек снова определяется по ФИО и году, а не по «id:…»
      const anchor = player || person.players[0];
      const targetKey = anchor ? baseKey(anchor) : person.key;
      location.hash = `#/player/${encodeURIComponent(targetKey)}${anchor ? '/' + anchor.id : ''}`;
      resetOverrides();
    });
    notice.append(reset);
    wrap.append(notice);
  }

  wrap.append(el('div', 'caption', 'Статистика игрока появится, когда федерация начнёт публиковать протоколы.'));
  return wrap;
}

function openMergeDialog(fromKey) {
  const overlay = el('div', 'modal');
  const sheet = el('div', 'sheet');
  const head = el('div', 'row pad');
  head.append(el('div', 'grow strong', 'Связать с игроком'));

  const close = el('button', 'icon-btn');
  close.append(icon('close'));
  head.append(close);
  sheet.append(head);
  const hint = el('div', 'small muted', 'Выберите запись, которая относится к тому же человеку — даже если имя написано иначе.');
  hint.style.padding = '0 16px 8px';
  sheet.append(hint);

  const box = el('div', 'searchbox');
  const input = el('input');
  input.type = 'search';
  input.placeholder = 'Фамилия или имя';
  box.append(input);
  sheet.append(box);

  const results = el('div', 'results');
  sheet.append(results);

  const paint = value => {
    const needle = fold(value);
    results.replaceChildren();
    const people = [...S.persons.values()]
      .filter(person => resolveKey(person.key) !== resolveKey(fromKey))
      .filter(person => !needle || fold(person.name).includes(needle))
      .sort((a, b) => a.name.localeCompare(b.name, 'ru'))
      .slice(0, 30);
    if (!people.length) {
      results.append(el('div', 'empty', 'Никого не нашли'));
      return;
    }
    for (const person of people) {
      const row = el('div', 'row pad');
      row.append(personAvatar(person));
      const text = el('div', 'grow');
      text.append(el('div', null, person.name));
      text.append(el('div', 'small muted', person.birthYear ? `${person.birthYear} г.р.` : ''));
      row.append(text);
      row.addEventListener('click', () => {
        mergePerson(fromKey, person.key);
        overlay.remove();
        location.hash = `#/player/${encodeURIComponent(resolveKey(person.key))}`;
      });
      results.append(row);
    }
  };
  paint('');

  input.addEventListener('input', () => paint(input.value));
  close.addEventListener('click', () => overlay.remove());
  overlay.addEventListener('click', event => { if (event.target === overlay) overlay.remove(); });
  overlay.append(sheet);
  document.body.append(overlay);
}

function screenGame(divisionId, gameId) {
  const game = S.data.games.find(g => String(g.id) === String(gameId) && g.divisionId === String(divisionId))
    || S.data.games.find(g => String(g.id) === String(gameId));
  if (!game) return el('div', 'empty', 'Игра не найдена');
  const division = S.divisions.get(game.divisionId);
  const wrap = document.createDocumentFragment();

  const card = el('div', 'card');
  card.append(el('div', 'strong', fullDate(game.date)));
  if (game.time) card.append(el('div', 'muted', game.time));
  if (game.venue) card.append(el('div', 'small muted', game.venue));
  wrap.append(card);

  if ((game.refs || []).length || game.comment) {
    const extra = el('div', 'card');
    if ((game.refs || []).length) {
      extra.append(el('div', 'small muted', 'Судьи'));
      extra.append(el('div', null, game.refs.join(', ')));
    }
    if (game.comment) {
      extra.append(el('div', 'small muted', 'Комментарий'));
      extra.append(el('div', null, game.comment));
    }
    wrap.append(extra);
  }

  const teams = el('div', 'card');
  const line = el('div', 'scoreline');
  const home = el('div', 'grow teamlink', game.home);
  home.addEventListener('click', () => { location.hash = `#/team/${game.homeId}`; });
  const away = el('div', 'grow right teamlink', game.away);
  away.addEventListener('click', () => { location.hash = `#/team/${game.awayId}`; });
  line.append(home);
  line.append(el('div', 'score', game.score || '—'));
  line.append(away);
  teams.append(line);
  wrap.append(teams);

  if (gameSets(game).length) {
    wrap.append(el('h2', 'section', 'Партии'));
    const sets = el('div', 'card flush');
    gameSets(game).forEach((set, index) => {
      const row = el('div', 'row pad');
      row.append(el('div', 'grow', `Партия ${index + 1}`));
      row.append(el('div', 'strong', `${set[0]} : ${set[1]}`));
      sets.append(row);
    });
    wrap.append(sets);
  }

  if (division) {
    const div = el('div', 'card');
    div.append(el('div', 'small muted', 'Дивизион'));
    div.append(el('div', null, `${division.competition} · ${divisionSubtitle(division)}`));
    div.addEventListener('click', () => { location.hash = `#/division/${division.id}`; });
    wrap.append(div);
  }

  const status = game.score
    ? 'Результат опубликован федерацией.'
    : (game.date ? 'Результат пока не опубликован.' : 'Дата игры ещё не назначена.');
  wrap.append(el('div', 'caption', status));
  return wrap;
}

function screenCalendar() {
  const wrap = document.createDocumentFragment();

  const toolbar = el('div', 'toolbar');
  const todayBtn = el('button', 'chip', 'Сегодня');
  todayBtn.addEventListener('click', () => {
    const now = new Date();
    S.calMonth = monthKey(now);
    S.calDay = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    S.calMode = 'month';
    render();
  });
  toolbar.append(el('div', 'grow'), todayBtn,
    iconButton('list', () => { S.calMode = 'list'; render(); }),
    iconButton('calendar', () => { S.calMode = 'month'; if (!S.calMonth) S.calMonth = monthKey(new Date()); render(); }));
  wrap.append(toolbar);

  const genders = [['all', 'Все'], ['fav', 'Мои'], ['boys', 'Юноши'], ['girls', 'Девушки'], ['men', 'Мужчины'], ['women', 'Женщины']];
  const chips = el('div', 'chips');
  for (const [value, label] of genders) {
    const chip = el('button', 'chip' + (S.gender === value ? ' on' : ''), label);
    chip.addEventListener('click', () => { S.gender = value; S.year = 0; S.calDivision = 0; render(); });
    chips.append(chip);
  }
  wrap.append(chips);

  let divisionFilter = () => true;
  if (S.gender === 'boys' || S.gender === 'girls' || S.gender === 'men' || S.gender === 'women') {
    const list = S.data.divisions
      .filter(d => d.gender === S.gender)
      .sort(compareDivisions);
    if (list.length) {
      const row = el('div', 'chips');
      const all = el('button', 'chip' + (S.calDivision === 0 ? ' on' : ''), 'Все');
      all.addEventListener('click', () => { S.calDivision = 0; render(); });
      row.append(all);
      for (const division of list) {
        const chip = el('button', 'chip' + (S.calDivision === division.id ? ' on' : ''), division.birthYear ? String(division.birthYear) : `${division.teamLabel}-я`);
        chip.addEventListener('click', () => { S.calDivision = division.id; render(); });
        row.append(chip);
      }
      wrap.append(row);
      if (S.calDivision) divisionFilter = game => game.divisionId === S.calDivision;
    }
  }

  const tracked = trackedTeamIds();
  const byGender = game => {
    if (S.gender === 'fav') return tracked.has(game.homeId) || tracked.has(game.awayId);
    if (S.gender === 'all') return true;
    const division = S.divisions.get(game.divisionId);
    return division && division.gender === S.gender;
  };
  const games = S.data.games.filter(game => byGender(game) && divisionFilter(game));

  const byDate = new Map();
  for (const game of games) {
    if (!game.date) continue;
    if (!byDate.has(game.date)) byDate.set(game.date, []);
    byDate.get(game.date).push(game);
  }
  for (const list of byDate.values()) list.sort(sortGames);

  const undated = games.filter(g => !g.date).sort((a, b) => a.divisionId - b.divisionId);

  if (S.calMode === 'month') {
    wrap.append(renderMonth(byDate));
    if (undated.length) {
      wrap.append(el('h2', 'section', `Дата не назначена · ${undated.length}`));
      for (const game of undated) wrap.append(gameCard(game));
    }
    return wrap;
  }

  const showPast = !!S.calPast;
  const tabs = el('div', 'seghead');
  for (const [value, label] of [[false, 'Ближайшие'], [true, 'Прошедшие']]) {
    const button = el('button', (value === showPast ? 'on ' : '') + 'segbtn', label);
    button.addEventListener('click', () => {
      if (value !== showPast) { anchorTabs(tabs); S.calPast = value; render(); }
    });
    tabs.append(button);
  }
  wrap.append(tabs);

  const dated = games.filter(g => g.date);
  const feed = (showPast
    ? dated.filter(isPastGame).sort((a, b) => sortGames(b, a))
    : dated.filter(g => !isPastGame(g)).sort(sortGames));
  if (!feed.length) {
    wrap.append(el('div', 'empty', showPast ? 'Прошедших игр пока нет' : 'Ближайших игр пока нет'));
  }
  const feedDates = [...new Set(feed.map(g => g.date))];
  feedDates.sort();
  if (showPast) feedDates.reverse();
  for (const date of feedDates) {
    wrap.append(el('h2', 'section', fullDate(date)));
    for (const game of feed.filter(g => g.date === date)) wrap.append(gameCard(game, { hideDate: true }));
  }

  if (!showPast && undated.length) {
    wrap.append(el('h2', 'section', `Дата не назначена · ${undated.length}`));
    for (const game of undated) wrap.append(gameCard(game));
  }
  return wrap;
}

function renderMonth(byDate) {
  const wrap = document.createDocumentFragment();
  const key = S.calMonth || monthKey(new Date());
  const [year, month] = key.split('-').map(Number);

  const head = el('div', 'monthhead');
  const prev = iconButton('list', () => {});
  prev.remove();
  const prevBtn = el('button', 'chip', '‹');
  prevBtn.addEventListener('click', () => {
    const d = new Date(year, month - 2, 1);
    S.calMonth = monthKey(d);
    S.calDay = null;
    render();
  });
  const nextBtn = el('button', 'chip', '›');
  nextBtn.addEventListener('click', () => {
    const d = new Date(year, month, 1);
    S.calMonth = monthKey(d);
    S.calDay = null;
    render();
  });
  const monthNames = ['январь', 'февраль', 'март', 'апрель', 'май', 'июнь', 'июль', 'август', 'сентябрь', 'октябрь', 'ноябрь', 'декабрь'];
  head.append(prevBtn, el('div', 'grow center strong', `${monthNames[month - 1]} ${year}`), nextBtn);
  wrap.append(head);

  const weekdays = ['пн', 'вт', 'ср', 'чт', 'пт', 'сб', 'вс'];
  const week = el('div', 'week');
  for (const name of weekdays) week.append(el('div', 'wd', name));
  wrap.append(week);

  const first = new Date(year, month - 1, 1);
  const offset = (first.getDay() + 6) % 7;
  const days = new Date(year, month, 0).getDate();
  const selected = S.calDay || (todayIso().startsWith(key) ? todayIso() : null);
  const grid = el('div', 'month');
  for (let i = 0; i < offset; i++) grid.append(el('div', 'cell empty'));
  for (let day = 1; day <= days; day++) {
    const iso = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    const cell = el('button', 'cell' + (byDate.has(iso) ? ' has' : '') + (selected === iso ? ' sel' : ''));
    cell.append(el('span', null, String(day)));
    if (byDate.has(iso)) cell.append(el('i', 'dot'));
    cell.addEventListener('click', () => {
      S.calDay = iso;
      S.calMonth = iso.slice(0, 7);
      render();
    });
    grid.append(cell);
  }
  wrap.append(grid);

  if (selected) {
    wrap.append(el('h2', 'section', fullDate(selected)));
    const list = byDate.get(selected) || [];
    if (!list.length) wrap.append(el('div', 'empty', 'В этот день игр нет'));
    for (const game of list) wrap.append(gameCard(game, { hideDate: true }));
  } else {
    wrap.append(el('div', 'empty', 'Выберите день с отметкой'));
  }
  return wrap;
}

function screenVenues() {
  const wrap = document.createDocumentFragment();
  const counts = new Map();
  for (const game of S.data.games) {
    if (!game.venue) continue;
    counts.set(game.venue, (counts.get(game.venue) || 0) + 1);
  }
  const list = [...counts.entries()].sort((a, b) => b[1] - a[1]);
  const card = el('div', 'card flush');
  for (const [venue, count] of list) {
    const row = el('div', 'row pad');
    const text = el('div', 'grow');
    text.append(el('div', null, venue));
    text.append(el('div', 'small muted', gamesWord(count)));
    row.append(text, chevron());
    row.addEventListener('click', () => { location.hash = `#/venue/${encodeURIComponent(venue)}`; });
    card.append(row);
  }
  wrap.append(card);
  return wrap;
}

function screenVenue(venue) {
  const wrap = document.createDocumentFragment();
  const games = S.data.games.filter(g => g.venue === venue).sort(sortGames);
  if (!games.length) wrap.append(el('div', 'empty', 'Игр нет'));
  for (const game of games) wrap.append(gameCard(game));
  return wrap;
}

function screenSearch(query) {
  const wrap = document.createDocumentFragment();
  const box = el('div', 'searchbox');
  const input = el('input');
  input.type = 'search';
  input.placeholder = 'Команда, игрок или дивизион';
  input.value = query || '';
  box.append(input);
  wrap.append(box);

  const results = el('div');
  wrap.append(results);

  const paint = value => {
    results.replaceChildren(buildSearchResults(value));
  };
  input.addEventListener('input', () => {
    history.replaceState(null, '', `#/search/${encodeURIComponent(input.value)}`);
    paint(input.value);
  });
  paint(query || '');
  return wrap;
}

function buildSearchResults(query) {
  const wrap = document.createDocumentFragment();
  const needle = fold(query || '');
  if (!needle) {
    wrap.append(el('div', 'empty', 'Начните вводить имя команды или игрока'));
    return wrap;
  }

  const teams = S.data.teams.filter(t => fold(t.name).includes(needle)).slice(0, 20);
  const persons = [...S.persons.values()].filter(p => fold(p.name).includes(needle)).slice(0, 30);
  const divisions = S.data.divisions
    .filter(d => fold(divisionSubtitle(d)).includes(needle) || fold(d.competition).includes(needle))
    .slice(0, 10);

  if (teams.length) {
    wrap.append(el('h2', 'section', 'Команды'));
    const card = el('div', 'card flush');
    for (const team of teams) {
      const division = S.divisions.get(team.divisionId);
      const row = el('div', 'row pad');
      row.append(teamLogo(team));
      const text = el('div', 'grow');
      text.append(el('div', null, team.name));
      text.append(el('div', 'small muted', division ? `${division.competition} · ${divisionSubtitle(division)}` : ''));
      row.append(text, chevron());
      row.addEventListener('click', () => { location.hash = `#/team/${team.id}`; });
      card.append(row);
    }
    wrap.append(card);
  }

  if (persons.length) {
    wrap.append(el('h2', 'section', 'Игроки'));
    const card = el('div', 'card flush');
    for (const person of persons) {
      const row = el('div', 'row pad');
      row.append(personAvatar(person));
      const text = el('div', 'grow');
      text.append(el('div', null, person.name));
      text.append(el('div', 'small muted', person.birthYear ? `${person.birthYear} г.р.` : ''));
      row.append(text, chevron());
      row.addEventListener('click', () => { location.hash = `#/player/${encodeURIComponent(person.key)}`; });
      card.append(row);
    }
    wrap.append(card);
  }

  if (divisions.length) {
    wrap.append(el('h2', 'section', 'Дивизионы'));
    const card = el('div', 'card flush');
    for (const division of divisions) {
      const row = el('div', 'row pad');
      const text = el('div', 'grow');
      text.append(el('div', null, division.competition));
      text.append(el('div', 'small muted', divisionSubtitle(division)));
      row.append(text, chevron());
      row.addEventListener('click', () => { location.hash = `#/division/${division.id}`; });
      card.append(row);
    }
    wrap.append(card);
  }

  if (!teams.length && !persons.length && !divisions.length) {
    wrap.append(el('div', 'empty', 'Ничего не найдено'));
  }
  return wrap;
}

function screenSettings() {
  const wrap = document.createDocumentFragment();

  wrap.append(el('h2', 'section', 'О проекте'));
  const about = el('div', 'card about');
  about.append(el('div', 'strong', 'Нравится приложение?'));
  about.append(el('p', null, 'Помоги развитию — поддержи проект.'));
  const donate = el('a', 'btn btn-accent donate', 'Поддержать проект');
  donate.href = 'https://spasibomir.ru/pay/35620';
  donate.target = '_blank';
  donate.rel = 'noopener';
  about.append(donate);
  about.append(el('p', null, 'VolleyPiter — некоммерческий проект, сделанный в помощь болельщикам и тренерам.'));
  const sources = el('p', null, 'Приложение не является официальным продуктом федерации. Данные собраны из открытых источников: ');
  const siteLink = el('a', 'contact', 'pitervolley.ru');
  siteLink.href = 'https://pitervolley.ru';
  siteLink.target = '_blank';
  siteLink.rel = 'noopener';
  const sheetLink = el('a', 'contact', 'таблицы расписания');
  sheetLink.href = 'https://docs.google.com/spreadsheets/d/1qVhAPMFcWAFylnoy2529uQzTXeBs6lOvfvpWbH2x6u4/edit';
  sheetLink.target = '_blank';
  sheetLink.rel = 'noopener';
  sources.append(siteLink, document.createTextNode(' и '), sheetLink, document.createTextNode('.'));
  about.append(sources);
  about.append(el('p', null, 'Для удаления контента напишите:'));
  const mail = el('a', 'contact', 'evsujkov@yandex.ru');
  mail.href = 'mailto:evsujkov@yandex.ru';
  about.append(mail);
  wrap.append(about);

  wrap.append(el('h2', 'section', 'Данные'));
  const dataGroup = el('div', 'group');
  const freshness = el('div', 'row pad');
  const freshnessText = el('div', 'grow');
  freshnessText.append(el('div', 'strong', 'Данные на'));
  freshnessText.append(el('div', 'small muted', dataFreshnessText()));
  freshness.append(freshnessText);
  dataGroup.append(freshness);

  const checked = el('div', 'row pad');
  const checkedRow = el('div', 'grow');
  checkedRow.append(el('div', 'strong', 'Последняя проверка'));
  checkedRow.append(el('div', 'small muted', checkedText()));
  checked.append(checkedRow);
  const refresh = el('button', 'btn btn-accent', 'Обновить');
  refresh.addEventListener('click', () => refreshAll(refresh));
  checked.append(refresh);
  dataGroup.append(checked);

  const source = el('div', 'row pad');
  const sourceText = el('div', 'grow');
  sourceText.append(el('div', 'strong', 'Источник'));
  sourceText.append(el('div', 'small muted', sourceLabel()));
  source.append(sourceText);
  dataGroup.append(source);

  const counts = el('div', 'row pad');
  const countsText = el('div', 'grow');
  countsText.append(el('div', 'strong', 'В базе'));
  countsText.append(el('div', 'small muted', `${divisionsWord(S.data.divisions.length)} · ${teamsWord(S.data.teams.length)} · ${playersWord(S.persons.size)}`));
  counts.append(countsText);
  dataGroup.append(counts);
  wrap.append(dataGroup);

  wrap.append(el('h2', 'section', 'Избранное'));
  const favGroup = el('div', 'group');
  const favRow = el('div', 'row pad');
  const favText = el('div', 'grow');
  favText.append(el('div', 'strong', 'Мои команды и игроки'));
  favText.append(el('div', 'small muted', `${teamsWord(S.favTeams.size)}, ${playersWord(S.favPersons.size)}`));
  favRow.append(favText);
  favGroup.append(favRow);

  if (S.favTeams.size || S.favPersons.size) {
    const resetRow = el('div', 'row pad');
    const reset = el('button', 'btn btn-ghost', 'Сбросить избранное');
    reset.addEventListener('click', () => {
      S.favTeams.clear();
      S.favPersons.clear();
      saveFavorites();
      render();
    });
    resetRow.append(reset);
    favGroup.append(resetRow);
  }
  wrap.append(favGroup);

  wrap.append(el('h2', 'section', 'Настройки экрана'));
  const screenGroup = el('div', 'group');
  const screenRow = el('div', 'row pad');
  const screenText = el('div', 'grow');
  screenText.append(el('div', 'strong', 'Фильтры и вид календаря'));
  screenText.append(el('div', 'small muted', 'Сохраняются на этом устройстве: выбранный пол, год, список или месяц.'));
  screenRow.append(screenText);
  screenGroup.append(screenRow);
  const resetRow = el('div', 'row pad');
  const resetFilters = el('button', 'btn btn-ghost', 'Сбросить фильтры');
  resetFilters.addEventListener('click', () => resetUi());
  resetRow.append(resetFilters);
  screenGroup.append(resetRow);
  wrap.append(screenGroup);

  wrap.append(el('h2', 'section', 'Напоминания'));
  const notes = el('div', 'card');
  notes.append(el('div', 'small muted', 'В веб-версии напоминаний нет: браузер не умеет будить приложение по расписанию так же надёжно, как Android. В приложении для Android напоминание приходит в 18:00 за день до игры.'));
  wrap.append(notes);

  wrap.append(el('h2', 'section', 'Установка на iPhone'));
  const install = el('div', 'card');
  install.append(el('div', 'small muted', 'Safari → «Поделиться» → «На экран Домой». Появится иконка, приложение откроется без адресной строки и будет работать офлайн.'));
  wrap.append(install);

  wrap.append(el('h2', 'section', 'Ручная группировка игроков'));
  const grouping = el('div', 'card');
  grouping.append(el('div', 'small muted', 'Правки на карточке игрока: «Это разные люди» и «Связать с игроком». Сейчас правок: ' +
    `${S.splits.size} ${plural(S.splits.size, 'разделение', 'разделения', 'разделений')}, ` +
    `${S.merges.size} ${plural(S.merges.size, 'объединение', 'объединения', 'объединений')}.`));
  if (S.splits.size || S.merges.size) {
    const reset = el('button', 'btn btn-ghost', 'Сбросить все правки');
    reset.style.marginTop = '10px';
    reset.addEventListener('click', () => resetOverrides());
    grouping.append(reset);
  }
  wrap.append(grouping);


  return wrap;
}

function shortMoment(source) {
  if (!source) return null;
  const date = new Date(source);
  if (Number.isNaN(date.getTime())) return null;
  const pad = value => String(value).padStart(2, '0');
  return `${pad(date.getDate())}.${pad(date.getMonth() + 1)} в ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function shortAge(source) {
  if (!source) return '';
  const date = new Date(source);
  if (Number.isNaN(date.getTime())) return '';
  const minutes = Math.floor((Date.now() - date.getTime()) / 60000);
  if (minutes < 1) return 'только что';
  if (minutes < 60) return `${minutes} мин назад`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} ч назад`;
  const days = Math.floor(hours / 24);
  return `${days} дн назад`;
}

function shortChecked() {
  const source = localStorage.getItem('vp.lastSync');
  const date = source ? new Date(source) : null;
  if (!date || Number.isNaN(date.getTime())) return 'не проверяли';
  return `проверено в ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

function formatMoment(source) {
  if (!source) return null;
  const date = new Date(source);
  if (Number.isNaN(date.getTime())) return null;
  const pad = value => String(value).padStart(2, '0');
  return `${pad(date.getDate())}.${pad(date.getMonth() + 1)}.${date.getFullYear()} в ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function relativeAge(source) {
  if (!source) return '';
  const date = new Date(source);
  if (Number.isNaN(date.getTime())) return '';
  const minutes = Math.floor((Date.now() - date.getTime()) / 60000);
  if (minutes < 1) return 'только что';
  if (minutes < 60) return `${minutes} ${plural(minutes, 'минуту', 'минуты', 'минут')} назад`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} ${plural(hours, 'час', 'часа', 'часов')} назад`;
  const days = Math.floor(hours / 24);
  return `${days} ${plural(days, 'день', 'дня', 'дней')} назад`;
}

function dataFreshnessText() {
  const stamp = formatMoment(S.data.generatedAt);
  if (!stamp) return 'данные ещё не загружены';
  return `${stamp} · ${relativeAge(S.data.generatedAt)}`;
}

function checkedText() {
  const stamp = formatMoment(localStorage.getItem('vp.lastSync'));
  return stamp ? `проверено ${stamp}` : 'ещё не проверяли';
}

function generatedText() {
  const stored = localStorage.getItem('vp.lastSync');
  const source = stored || S.data.generatedAt;
  if (!source) return 'ещё не обновляли';
  const d = new Date(source);
  if (Number.isNaN(d.getTime())) return 'ещё не обновляли';
  const pad = value => String(value).padStart(2, '0');
  return `${pad(d.getDate())}.${pad(d.getMonth() + 1)}.${d.getFullYear()} в ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function sourceLabel() {
  const sources = S.data.sources;
  if (sources) {
    if (sources.site && sources.sheet) return 'pitervolley.ru + volleyreferee.spb.ru';
    if (sources.site) return 'только pitervolley.ru — расписание недоступно';
    if (sources.sheet) return 'только volleyreferee.spb.ru — сайт недоступен';
    return 'источники недоступны';
  }
  return sheetUsed() ? 'pitervolley.ru + volleyreferee.spb.ru' : 'pitervolley.ru';
}

function sheetUsed() {
  if (S.data.counts && S.data.counts.datesFromSheet > 0) return true;
  return S.data.games.some(game => game.dateSource === 'sheet');
}

/* ------------------------------------------------------------------ router */

const TITLES = {
  home: ['VolleyPiter', 'Волейбол СПб'],
  divisions: ['Дивизионы', 'Первенство и Чемпионат'],
  calendar: ['Календарь', 'Расписание игр'],
  venues: ['Залы', 'Адреса игр'],
  search: ['Поиск', 'Команды, игроки, дивизионы'],
  settings: ['Настройки', 'VolleyPiter'],
};

function setHeader(title, subtitle, showBack) {
  $('#title').textContent = title;
  $('#subtitle').textContent = subtitle || '';
  $('#backBtn').hidden = !showBack;
}

function render() {
  if (!S.data) return;
  const raw = (location.hash || '#/home').replace(/^#\/?/, '');
  const parts = raw.split('/').filter(Boolean);
  const route = parts[0] || 'home';
  let content;
  let title = TITLES[route] ? TITLES[route][0] : 'VolleyPiter';
  let subtitle = TITLES[route] ? TITLES[route][1] : '';
  let back = false;

  if (route === 'home') {
    content = screenHome();
  } else if (route === 'divisions') {
    content = screenDivisions();
    subtitle = `Сезон ${S.data.season}`;
  } else if (route === 'division') {
    const division = S.divisions.get(parts[1]);
    content = screenDivision(parts[1], parts[2]);
    if (division) { title = division.competition; subtitle = divisionSubtitle(division); }
    back = true;
  } else if (route === 'team') {
    const team = S.teams.get(parts[1]);
    content = screenTeam(parts[1]);
    if (team) {
      title = team.name;
      const division = S.divisions.get(team.divisionId);
      subtitle = division ? `${division.competition} · ${divisionSubtitle(division)}` : '';
    }
    back = true;
  } else if (route === 'player') {
    const key = decodeURIComponent(parts[1] || '');
    const playerId = parts[2] ? decodeURIComponent(parts[2]) : null;
    const person = S.persons.get(key);
    content = screenPlayer(key, playerId);
    if (person) title = person.name;
    back = true;
  } else if (route === 'game') {
    const game = S.data.games.find(g => String(g.id) === String(parts[2]));
    content = screenGame(parts[1], parts[2]);
    if (game) {
      title = `${game.home} — ${game.away}`;
      const division = S.divisions.get(game.divisionId);
      if (division) subtitle = `${division.competition} · ${divisionSubtitle(division)}`;
    }
    back = true;
  } else if (route === 'calendar') {
    if (parts[1] === 'month') S.calMode = 'month';
    if (parts[1] === 'list') S.calMode = 'list';
    content = screenCalendar();
  } else if (route === 'venues') {
    content = screenVenues();
    back = true;
  } else if (route === 'venue') {
    const venue = decodeURIComponent(parts[1] || '');
    content = screenVenue(venue);
    title = venue;
    back = true;
  } else if (route === 'search') {
    const query = decodeURIComponent(parts[1] || '');
    content = screenSearch(query);
    title = 'Поиск';
    subtitle = 'Команды, игроки, дивизионы';
    back = true;
  } else if (route === 'settings') {
    content = screenSettings();
    back = true;
  } else {
    content = el('div', 'empty', 'Страница не найдена');
  }

  setHeader(title, subtitle, back);
  app.replaceChildren(content);
  restoreScroll();
  document.querySelectorAll('#tabbar a').forEach(a => {
    a.classList.toggle('on', a.dataset.tab === route);
  });
  saveUi();
}

$('#backBtn').addEventListener('click', () => { history.length > 1 ? history.back() : location.hash = '#/home'; });
$('#searchBtn').addEventListener('click', () => { location.hash = '#/search/'; render(); });
$('#settingsBtn').addEventListener('click', () => { location.hash = '#/settings'; render(); });

window.addEventListener('hashchange', render);
restoreUi();
loadData(true);

// возврат в приложение: если данные старше 10 минут — обновляем
document.addEventListener('visibilitychange', () => {
  if (document.hidden) return;
  const stamp = localStorage.getItem('vp.lastSync');
  const last = stamp ? new Date(stamp).getTime() : 0;
  if (!last || Date.now() - last > 10 * 60 * 1000) loadData(true);
});

if ('serviceWorker' in navigator) {
  const hadController = !!navigator.serviceWorker.controller;
  let reloading = false;
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (!hadController || reloading) return;
    reloading = true;
    location.reload();
  });
  window.addEventListener('load', async () => {
    try {
      const registration = await navigator.serviceWorker.register('sw.js', { updateViaCache: 'none' });
      await registration.update();
      if (registration.waiting) registration.waiting.postMessage({ type: 'SKIP_WAITING' });
    } catch (error) {
      // без service worker приложение всё равно работает
    }
  });
}
