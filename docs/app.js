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
  gender: 'all',
  year: 0,
  searchQuery: '',
};

function saveFavorites() {
  localStorage.setItem('vp.favTeams', JSON.stringify([...S.favTeams]));
  localStorage.setItem('vp.favPersons', JSON.stringify([...S.favPersons]));
}

function isFavTeam(id) { return S.favTeams.has(id); }
function isFavPerson(key) { return S.favPersons.has(key); }
function toggleFavTeam(id) {
  if (S.favTeams.has(id)) S.favTeams.delete(id); else S.favTeams.add(id);
  saveFavorites();
  render();
}
function toggleFavPerson(key) {
  if (S.favPersons.has(key)) S.favPersons.delete(key); else S.favPersons.add(key);
  saveFavorites();
  render();
}

function trackedTeamIds() {
  const ids = new Set(S.favTeams);
  for (const key of S.favPersons) {
    const person = S.persons.get(key);
    if (person) for (const team of person.teams) ids.add(team.id);
  }
  return ids;
}

/* ------------------------------------------------------------------ data */

function buildIndexes(data) {
  S.divisions.clear();
  S.teams.clear();
  S.persons.clear();
  S.playerPerson.clear();

  for (const d of data.divisions) S.divisions.set(d.id, d);
  for (const t of data.teams) S.teams.set(t.id, t);

  for (const p of data.players) {
    const key = personKey(p.name, p.birthYear);
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
    S.playerPerson.set(p.id, personKey(p.name, p.birthYear));
  }
}

async function loadData() {
  try {
    const response = await fetch('data.json', { cache: 'no-cache' });
    if (!response.ok) throw new Error('HTTP ' + response.status);
    S.data = await response.json();
    buildIndexes(S.data);
    localStorage.setItem('vp.data', JSON.stringify({ at: Date.now(), data: S.data }));
  } catch (error) {
    const cached = localStorage.getItem('vp.data');
    if (cached) {
      S.data = JSON.parse(cached).data;
      buildIndexes(S.data);
    } else {
      app.innerHTML = '<div class="empty">Не удалось загрузить данные. Проверьте интернет и обновите страницу.</div>';
      return;
    }
  }
  document.addEventListener('online', updateOnline);
  document.addEventListener('offline', updateOnline);
  updateOnline();
  render();
}

function updateOnline() {
  $('#offline').hidden = navigator.onLine;
}

/* ------------------------------------------------------------------ games */

function gamesOfDivision(divisionId) {
  return S.data.games.filter(g => g.divisionId === divisionId);
}

function isChampionship(division) {
  return !!division && division.competition.toLowerCase().includes('чемпионат');
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
  comp.append(el('span', 'dot', '●'), document.createTextNode(' ' + (division ? `${division.competition} · ${divisionSubtitle(division)}` : '')));
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
      img.src = logo.logo;
      img.alt = '';
      img.loading = 'lazy';
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

function screenHome() {
  const tracked = trackedTeamIds();
  const upcoming = S.data.games
    .filter(g => g.date && g.date >= todayIso())
    .sort(sortGames)
    .slice(0, 6);
  const myGames = S.data.games
    .filter(g => g.date && g.date >= todayIso() && (tracked.has(g.homeId) || tracked.has(g.awayId)))
    .sort(sortGames)
    .slice(0, 6);
  const played = S.data.games.filter(g => g.score).length;
  const venues = new Set(S.data.games.map(g => g.venue).filter(Boolean));

  const wrap = document.createDocumentFragment();

  const stats = el('div', 'stats');
  const statItems = [
    [S.data.divisions.length, 'дивизионов'],
    [S.data.teams.length, 'команд'],
    [S.persons.size, 'игроков'],
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
  updatedText.append(el('div', 'small muted', 'Последнее обновление'));
  const generated = S.data.generatedAt ? new Date(S.data.generatedAt) : null;
  const stamp = generated
    ? `${String(generated.getDate()).padStart(2, '0')}.${String(generated.getMonth() + 1).padStart(2, '0')}.${generated.getFullYear()} в ${String(generated.getHours()).padStart(2, '0')}:${String(generated.getMinutes()).padStart(2, '0')}`
    : 'нет данных';
  updatedText.append(el('div', 'strong', stamp));
  const sheetUsed = (S.data.counts && S.data.counts.datesFromSheet > 0) || S.data.games.some(g => g.dateSource === 'sheet');
  updatedText.append(el('div', 'small muted', sheetUsed ? 'Данные: pitervolley.ru + volleyreferee.spb.ru' : 'Данные: pitervolley.ru'));
  updatedRow.append(updatedText);
  const refresh = el('button', 'chip on', 'Обновить');
  refresh.addEventListener('click', async () => {
    refresh.textContent = 'Обновляем…';
    await loadData();
  });
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
      row.append(text, el('span', 'chev', '›'));
      row.addEventListener('click', () => { location.hash = `#/team/${team.id}`; });
      card.append(row);
    }
    wrap.append(card);
  }

  const favPersonList = [...S.favPersons].map(key => S.persons.get(key)).filter(Boolean);
  if (favPersonList.length) {
    wrap.append(el('h2', 'section', 'Мои игроки'));
    const card = el('div', 'card flush');
    for (const person of favPersonList) {
      const row = el('div', 'row pad');
      row.append(personAvatar(person));
      const text = el('div', 'grow');
      text.append(el('div', null, person.name));
      text.append(el('div', 'small muted', person.teams.length > 1 ? teamsWord(person.teams.length) : (person.teams[0] ? person.teams[0].name : '')));
      row.append(text, el('span', 'chev', '›'));
      row.addEventListener('click', () => { location.hash = `#/player/${encodeURIComponent(person.key)}`; });
      card.append(row);
    }
    wrap.append(card);
  }

  if (myGames.length) {
    wrap.append(el('h2', 'section', 'Их ближайшие игры'));
    for (const game of myGames) wrap.append(gameCard(game));
  }

  wrap.append(el('h2', 'section', 'Ближайшие игры'));
  if (!upcoming.length) wrap.append(el('div', 'empty', 'Ближайших игр пока нет'));
  for (const game of upcoming) wrap.append(gameCard(game));

  wrap.append(el('h2', 'section', 'Соревнования'));
  const competitions = el('div', 'stats');
  for (const [title, gender, note] of [['Первенство', 'boys', 'Юноши и девушки'], ['Чемпионат', 'men', 'Мужчины и женщины']]) {
    const count = S.data.divisions.filter(d => d.competition.includes(title)).length;
    const box = el('div', 'stat');
    box.append(el('b', null, String(count)), el('span', null, 'дивизионов'));
    box.append(el('div', 'small muted', note));
    box.addEventListener('click', () => { S.gender = gender; location.hash = '#/divisions'; render(); });
    competitions.append(box);
  }
  wrap.append(competitions);

  wrap.append(el('h2', 'section', 'Залы'));
  const venueCard = el('div', 'card');
  const venueRow = el('div', 'row');
  const venueText = el('div', 'grow');
  venueText.append(el('div', 'strong', 'Адреса игр'));
  venueText.append(el('div', 'small muted', venuesWord(venues.size) + ' · расписание по каждому'));
  venueRow.append(venueText, el('span', 'chev', '›'));
  venueCard.append(venueRow);
  venueCard.addEventListener('click', () => { location.hash = '#/venues'; });
  wrap.append(venueCard);

  return wrap;
}

function teamLogo(team) {
  if (team && team.logo) {
    const img = el('img', 'logo');
    img.src = team.logo;
    img.alt = '';
    img.loading = 'lazy';
    return img;
  }
  return el('div', 'avatar');
}

function personAvatar(person) {
  if (person && person.photo) {
    const img = el('img', 'avatar');
    img.src = person.photo;
    img.alt = '';
    img.loading = 'lazy';
    return img;
  }
  return el('div', 'avatar');
}

function screenDivisions() {
  const wrap = document.createDocumentFragment();
  const genders = [
    ['all', 'Все'], ['boys', 'Юноши'], ['girls', 'Девушки'], ['men', 'Мужчины'], ['women', 'Женщины'],
  ];
  const chips = el('div', 'chips');
  for (const [value, label] of genders) {
    const chip = el('button', 'chip' + (S.gender === value ? ' on' : ''), label);
    chip.addEventListener('click', () => { S.gender = value; S.year = 0; render(); });
    chips.append(chip);
  }
  wrap.append(chips);

  const list = S.data.divisions
    .filter(d => S.gender === 'all' || d.gender === S.gender)
    .sort((a, b) => (a.birthYear || 0) - (b.birthYear || 0) || (a.teamLabel || 0) - (b.teamLabel || 0));

  if (S.gender === 'boys' || S.gender === 'girls') {
    const years = [...new Set(list.map(d => d.birthYear))].filter(Boolean);
    const yearChips = el('div', 'chips');
    const all = el('button', 'chip' + (S.year === 0 ? ' on' : ''), 'Все');
    all.addEventListener('click', () => { S.year = 0; render(); });
    yearChips.append(all);
    for (const year of years) {
      const chip = el('button', 'chip' + (S.year === year ? ' on' : ''), String(year));
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
    const card = el('div', 'card');
    card.append(el('div', 'strong', division.competition));
    card.append(el('div', 'muted', divisionSubtitle(division)));
    card.append(el('div', 'small muted', `${teamsWord(teams.length)} · ${playedCount} из ${gamesWord(games.length)}`));
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
      const name = el('td', 'name', row.team.name);
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
      tr.append(el('td', 'name', row.name));
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
      row.append(text, el('span', 'chev', '›'));
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
  info.append(el('div', 'muted', division ? `${division.competition} · ${divisionSubtitle(division)}` : ''));
  profile.append(info);
  const star = el('button', 'star' + (isFavTeam(team.id) ? ' on' : ''), isFavTeam(team.id) ? '★' : '☆');
  star.addEventListener('click', () => toggleFavTeam(team.id));
  profile.append(star);
  wrap.append(profile);

  const players = S.data.players.filter(p => p.teamId === team.id);
  wrap.append(el('h2', 'section', `Состав · ${players.length}`));
  if (!players.length) {
    const card = el('div', 'card');
    card.append(el('div', 'strong', 'Заявка ещё не опубликована'));
    card.append(el('div', 'small muted', 'Федерация заполняет составы постепенно — они появляются на pitervolley.ru.'));
    wrap.append(card);
  }
  const grid = el('div', 'card flush');
  for (const player of players) {
    const key = S.playerPerson.get(player.id);
    const person = S.persons.get(key);
    const row = el('div', 'row pad');
    row.append(personAvatar(person || player));
    const text = el('div', 'grow');
    text.append(el('div', null, player.name));
    if (player.birthYear) text.append(el('div', 'small muted', `${player.birthYear} г.р.`));
    row.append(text, el('span', 'chev', '›'));
    row.addEventListener('click', () => { location.hash = `#/player/${encodeURIComponent(key)}`; });
    grid.append(row);
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

function screenPlayer(key) {
  const person = S.persons.get(key);
  if (!person) return el('div', 'empty', 'Игрок не найден');
  const wrap = document.createDocumentFragment();
  const profile = el('div', 'profile');
  profile.append(personAvatar(person));
  const info = el('div');
  info.append(el('h3', null, person.name));
  if (person.birthYear) info.append(el('div', 'muted', `${person.birthYear} г.р.`));
  profile.append(info);
  const star = el('button', 'star' + (isFavPerson(key) ? ' on' : ''), isFavPerson(key) ? '★' : '☆');
  star.addEventListener('click', () => toggleFavPerson(key));
  profile.append(star);
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
      row.append(text, el('span', 'chev', '›'));
      row.addEventListener('click', () => { location.hash = `#/team/${team.id}`; });
      card.append(row);
    }
    wrap.append(card);
  }

  wrap.append(el('div', 'notice', 'Статистика игрока появится, когда федерация начнёт публиковать протоколы.'));
  return wrap;
}

function screenGame(divisionId, gameId) {
  const game = S.data.games.find(g => String(g.id) === String(gameId) && g.divisionId === Number(divisionId))
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
  line.append(el('div', 'grow', game.home));
  line.append(el('div', 'score', game.score || '—'));
  line.append(el('div', 'grow right', game.away));
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
  const genders = [['all', 'Все'], ['fav', 'Мои'], ['boys', 'Юноши'], ['girls', 'Девушки'], ['men', 'Мужчины'], ['women', 'Женщины']];
  const chips = el('div', 'chips');
  for (const [value, label] of genders) {
    const chip = el('button', 'chip' + (S.gender === value ? ' on' : ''), label);
    chip.addEventListener('click', () => { S.gender = value; S.year = 0; render(); });
    chips.append(chip);
  }
  wrap.append(chips);

  const tracked = trackedTeamIds();
  const filterGame = game => {
    if (S.gender === 'fav') return tracked.has(game.homeId) || tracked.has(game.awayId);
    if (S.gender === 'all') return true;
    const division = S.divisions.get(game.divisionId);
    return division && division.gender === S.gender;
  };

  const games = S.data.games.filter(filterGame);
  const byDate = new Map();
  for (const game of games) {
    if (!game.date) continue;
    if (!byDate.has(game.date)) byDate.set(game.date, []);
    byDate.get(game.date).push(game);
  }
  const dates = [...byDate.keys()].sort();
  const undated = games.filter(g => !g.date).sort((a, b) => a.divisionId - b.divisionId);

  const future = dates.filter(d => d >= todayIso());
  const start = future.length ? future[0] : dates[dates.length - 1];

  if (!dates.length && !undated.length) wrap.append(el('div', 'empty', 'По выбранным фильтрам игр нет'));
  for (const date of dates.filter(d => d >= start)) {
    wrap.append(el('h2', 'section', fullDate(date)));
    for (const game of byDate.get(date).sort(sortGames)) {
      wrap.append(gameCard(game, { hideDate: true }));
    }
  }
  if (undated.length) {
    wrap.append(el('h2', 'section', `Дата не назначена · ${undated.length}`));
    for (const game of undated) wrap.append(gameCard(game));
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
    row.append(text, el('span', 'chev', '›'));
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
      row.append(text, el('span', 'chev', '›'));
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
      row.append(text, el('span', 'chev', '›'));
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
      row.append(text, el('span', 'chev', '›'));
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

  wrap.append(el('h2', 'section', 'Избранное'));
  const card = el('div', 'card');
  card.append(el('div', null, `Команд: ${S.favTeams.size}, игроков: ${S.favPersons.size}`));
  const reset = el('button', 'chip', 'Сбросить избранное');
  reset.style.marginTop = '10px';
  reset.addEventListener('click', () => {
    S.favTeams.clear();
    S.favPersons.clear();
    saveFavorites();
    render();
  });
  card.append(reset);
  wrap.append(card);

  wrap.append(el('h2', 'section', 'Напоминания'));
  const notes = el('div', 'card');
  notes.append(el('div', 'small muted', 'В веб-версии напоминаний пока нет. На Android-приложении они приходят в 18:00 за день до игры.'));
  wrap.append(notes);

  wrap.append(el('h2', 'section', 'О проекте'));
  const about = el('div', 'card about');
  about.append(el('p', null, 'VolleyPiter — некоммерческий проект, сделанный в помощь болельщикам и тренерам.'));
  about.append(el('p', null, 'Приложение не является официальным продуктом федерации. Данные собраны из открытых источников: pitervolley.ru и таблицы расписания.'));
  about.append(el('p', null, 'Для удаления контента напишите:'));
  const mail = el('a', 'contact', 'evsujkov@yandex.ru');
  mail.href = 'mailto:evsujkov@yandex.ru';
  about.append(mail);
  wrap.append(about);

  wrap.append(el('h2', 'section', 'Установка на iPhone'));
  const install = el('div', 'card');
  install.append(el('div', 'small muted', 'Откройте сайт в Safari → «Поделиться» → «На экран Домой». Приложение появится как отдельная иконка и будет работать офлайн.'));
  wrap.append(install);

  return wrap;
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
  } else if (route === 'division') {
    const division = S.divisions.get(Number(parts[1]));
    content = screenDivision(Number(parts[1]), parts[2]);
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
    const person = S.persons.get(key);
    content = screenPlayer(key);
    if (person) title = person.name;
    back = true;
  } else if (route === 'game') {
    const game = S.data.games.find(g => String(g.id) === String(parts[2]));
    content = screenGame(parts[1], parts[2]);
    if (game) title = `${game.home} — ${game.away}`;
    back = true;
  } else if (route === 'calendar') {
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
  window.scrollTo(0, 0);
  document.querySelectorAll('#tabbar a').forEach(a => {
    a.classList.toggle('on', a.dataset.tab === route);
  });
}

$('#backBtn').addEventListener('click', () => { history.length > 1 ? history.back() : location.hash = '#/home'; });
$('#searchBtn').addEventListener('click', () => { location.hash = '#/search/'; render(); });

window.addEventListener('hashchange', render);
loadData();

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  });
}
