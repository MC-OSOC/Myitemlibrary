// Joins the server as an offline-mode player, opens /my-library and claims every item by clicking it.
// The first item is clicked twice in a row (double click). Prints one JSON line with what it saw:
//   RESULT {"initial":N,"claimed":K,"final":M}
// Usage: node claim-bot.js <host> <port> <username> <mc-version>
const mineflayer = require('mineflayer');

const [host, port, username, version] = process.argv.slice(2);
const GUI_ITEM_SLOTS = 45; // slots 0-44 hold items, 45-53 are buttons

const bot = mineflayer.createBot({ host, port: Number(port), username, version, auth: 'offline' });

const fail = (msg) => { console.error('BOT ERROR: ' + msg); process.exit(1); };
setTimeout(() => fail('timed out'), 180000).unref();
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

async function openLibrary() {
  const opened = waitFor('windowOpen', 15000);
  bot.chat('/my-library');
  const window = await opened;
  await sleep(500); // let the slot contents arrive
  return window;
}

function libraryItems(window) {
  return window.slots.slice(0, GUI_ITEM_SLOTS).filter((s) => s);
}

bot.once('spawn', async () => {
  try {
    await sleep(2000);
    let window = await openLibrary();
    const initial = libraryItems(window).length;
    console.log('BOT library shows ' + initial + ' item(s): ' + libraryItems(window).map((s) => s.name).join(', '));
    let claimed = 0;
    let items = libraryItems(window);
    while (items.length > 0 && claimed < 20) {
      const slot = items[0].slot;
      const closed = waitFor('windowClose', 10000);
      if (claimed === 0) {
        // Double click: send both clicks without waiting. Only one claim may happen.
        bot.clickWindow(slot, 0, 0).catch(() => {});
        bot.clickWindow(slot, 0, 0).catch(() => {});
      } else {
        bot.clickWindow(slot, 0, 0).catch(() => {});
      }
      await closed; // the plugin closes the GUI when an item is clicked
      claimed++;
      await sleep(1500); // the reward command runs a tick later
      window = await openLibrary();
      items = libraryItems(window);
    }
    const final = items.length;
    bot.closeWindow(window);
    console.log('RESULT ' + JSON.stringify({ initial, claimed, final }));
    await sleep(500);
    bot.quit();
    setTimeout(() => process.exit(0), 1000);
  } catch (e) {
    fail(e.stack || String(e));
  }
});
