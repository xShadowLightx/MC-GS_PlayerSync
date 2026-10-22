const SHEET_NAME = 'Players_API_DO_NOT_TOUCH';
const SHARED_SECRET = 'PUT_A_LONG_RANDOM_SHARED_SECRET_HERE';

function doPost(e) {
  try {
    const body = JSON.parse(e.postData.contents);

    if (body.secret !== SHARED_SECRET) {
      return json({ ok: false, error: 'unauthorized' });
    }

    const player = body.player || {};

    const uuid = String(player.uuid || '');
    const name = String(player.name || '');
    const firstJoin = player.firstJoin ? new Date(player.firstJoin) : '';
    const lastJoin = player.lastJoin ? new Date(player.lastJoin) : '';
    const role = String(player.role || '');
    const playTimeHours = Number(player.playHours || 0);
    const balance = player.balance == null ? '' : Number(player.balance);
    const deaths = Number(player.deaths || 0);
    const playerKills = Number(player.playerKills || 0);

    if (!uuid) {
      return json({ ok: false, error: 'missing uuid' });
    }

    const sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(SHEET_NAME);
    if (!sheet) {
      return json({ ok: false, error: 'sheet not found' });
    }

    const data = sheet.getDataRange().getValues();

    let foundRow = -1;
    for (let i = 1; i < data.length; i++) {
      if (String(data[i][0]) === uuid) {
        foundRow = i + 1;
        break;
      }
    }

    if (foundRow === -1) {
      sheet.appendRow([uuid, name, firstJoin, lastJoin, role, playTimeHours, balance, deaths, playerKills]);
    } else {
      sheet.getRange(foundRow, 2, 1, 8).setValues([[name, firstJoin, lastJoin, role, playTimeHours, balance, deaths, playerKills]]);
    }

    return json({ ok: true, uuid: uuid, name: name, role: role, playTimeHours: playTimeHours, balance: balance, deaths: deaths, playerKills: playerKills });
  } catch (err) {
    return json({ ok: false, error: String(err) });
  }
}

function json(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
