// Headless Minecraft client used by smoke-test.sh. Joins as an offline-mode player and drives /my-library.
// Prints one line "RESULT {json}" with what it saw.
//
// Usage: node claim-bot.js <host> <port> <username> <mc-version> [mode] [claim-delay-ms] [physics]
// physics: "on" (default) or "off". Off = the bot never sends movement packets; used through ViaBackwards,
// where translated movement packets get the bot kicked for invalid movement. The bot does not need to move.
// Modes:
//   claim  (default) claim every item by clicking it; the first one is double-clicked.
//          RESULT {"initial":N,"claimed":K,"final":M}
//   guard  click item 1, then reopen and click item 2 while claim 1 is still running (server-side test delay):
//          the second click must be ignored. Then claim item 2 normally.
//          RESULT {"initial":N,"blocked":bool,"afterFirst":N,"final":M}
//   leave  click the first item and disconnect immediately (before the delayed reward runs).
//          RESULT {"initial":N}
//   slow   open the library once and measure how long it takes and the largest gap between the server's
//          time updates (sent every second by the main thread) meanwhile.
//          RESULT {"initial":N,"openMs":T,"maxGapMs":G}
const mineflayer = require('mineflayer');

const [host, port, username, version, mode = 'claim', delayArg = '0', physics = 'on'] = process.argv.slice(2);
const claimDelay = Number(delayArg);
const GUI_ITEM_SLOTS = 45; // slots 0-44 hold items, 45-53 are buttons

const bot = mineflayer.createBot({
  host, port: Number(port), username, version, auth: 'offline', physicsEnabled: physics !== 'off',
});

const fail = (msg) => { console.error('BOT ERROR: ' + msg); process.exit(1); };
setTimeout(() => fail('timed out'), 200000).unref();
bot.on('kicked', (reason) => fail('kicked: ' + JSON.stringify(reason)));
bot.on('error', (err) => fail(String(err && err.stack || err)));

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function waitFor(event, ms) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => { bot.removeListener(event, on); reject(new Error('no ' + event + ' within ' + ms + 'ms')); }, ms);
    const on = (arg) => { clearTimeout(timer); resolve(arg); };
    bot.once(event, on);
  });
}

async function openLibrary(timeoutMs = 15000) {
  const opened = waitFor('windowOpen', timeoutMs);
  bot.chat('/my-library');
  const window = await opened;
  await sleep(500); // let the slot contents arrive
  return window;
}

const items = (window) => window.slots.slice(0, GUI_ITEM_SLOTS).filter((s) => s);

async function click(window, slot, expectClose = true, ms = 10000) {
  const closed = waitFor('windowClose', ms);
  bot.clickWindow(slot, 0, 0).catch(() => {});
  if (!expectClose) return closed.then(() => true, () => false);
  await closed;
  return true;
}

function finish(result) {
  console.log('RESULT ' + JSON.stringify(result));
  setTimeout(() => { bot.quit(); setTimeout(() => process.exit(0), 1000); }, 300);
}

async function claimAll() {
  let window = await openLibrary();
  const initial = items(window).length;
  console.log('BOT library shows ' + initial + ' item(s)');
  let claimed = 0;
  let list = items(window);
  while (list.length > 0 && claimed < 20) {
    const slot = list[0].slot;
    const closed = waitFor('windowClose', 10000);
    if (claimed === 0) {
      // Double click: both clicks are sent before the server answers.
      bot.clickWindow(slot, 0, 0).catch(() => {});
      bot.clickWindow(slot, 0, 0).catch(() => {});
    } else {
      bot.clickWindow(slot, 0, 0).catch(() => {});
    }
    await closed; // the plugin closes the GUI when an item is clicked
    claimed++;
    await sleep(claimDelay + 1500); // the reward command runs after the (test) delay, a tick later
    window = await openLibrary();
    list = items(window);
  }
  bot.closeWindow(window);
  return { initial, claimed, final: list.length };
}

async function guard() {
  let window = await openLibrary();
  const initial = items(window).length;
  await click(window, items(window)[0].slot); // claim 1 starts, GUI closes
  // Claim 1 is still running (test delay). Reopen and click the next item: must be ignored, GUI stays open.
  window = await openLibrary();
  const second = items(window)[0];
  const closed = second ? await click(window, second.slot, false, 1500) : true;
  const blocked = !!second && !closed;
  if (!closed) bot.closeWindow(window);
  await sleep(claimDelay + 1500);
  window = await openLibrary();
  const afterFirst = items(window).length;
  bot.closeWindow(window);
  const rest = await claimAll();
  return { initial, blocked, afterFirst, final: rest.final };
}

async function leave() {
  const window = await openLibrary();
  const initial = items(window).length;
  bot.clickWindow(items(window)[0].slot, 0, 0).catch(() => {});
  await sleep(200); // let the click packet go out, then disconnect while the claim is still running
  console.log('RESULT ' + JSON.stringify({ initial }));
  bot.end('leaving mid-claim');
  setTimeout(() => process.exit(0), 500);
}

async function slow() {
  let last = Date.now();
  let maxGapMs = 0;
  let measuring = true;
  bot.on('time', () => {
    const now = Date.now();
    if (measuring) maxGapMs = Math.max(maxGapMs, now - last);
    last = now;
  });
  const start = Date.now();
  last = start;
  const window = await openLibrary(30000);
  const openMs = Date.now() - start;
  measuring = false;
  const initial = items(window).length;
  bot.closeWindow(window);
  return { initial, openMs, maxGapMs };
}

bot.once('spawn', async () => {
  try {
    if (mode !== 'slow') await sleep(2000);
    if (mode === 'claim') finish(await claimAll());
    else if (mode === 'guard') finish(await guard());
    else if (mode === 'leave') await leave();
    else if (mode === 'slow') finish(await slow());
    else fail('unknown mode ' + mode);
  } catch (e) {
    fail(e.stack || String(e));
  }
});
