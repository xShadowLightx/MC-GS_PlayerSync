const SHEET_NAME = 'Players_API_DO_NOT_TOUCH';
const SHARED_SECRET = 'PUT_A_LONG_RANDOM_SHARED_SECRET_HERE';

function doPost(e) {
  try {
    const body = JSON.parse(e.postData.contents);

    if (body.secret !== SHARED_SECRET) {
      return json({ ok: false, error: 'unauthorized' });
    }

    if (body.action === 'delete') {
      const uuidToDelete = String(body.uuid || '');
      if (!uuidToDelete) {
        return json({ ok: false, error: 'missing uuid' });
      }

      const deleteSheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(SHEET_NAME);
      if (!deleteSheet) {
        return json({ ok: false, error: 'sheet not found' });
      }

      const deleteData = deleteSheet.getDataRange().getValues();
      for (let i = 1; i < deleteData.length; i++) {
        if (String(deleteData[i][0]) === uuidToDelete) {
          deleteSheet.deleteRow(i + 1);
          return json({ ok: true, deleted: true, uuid: uuidToDelete });
        }
      }
      return json({ ok: true, deleted: false, uuid: uuidToDelete });
    }

    const player = body.player || {};
    const redacted = player.redacted === true;

    const uuid = String(player.uuid || '');
    const name = redacted ? String(player.name || '') : player.name === 'N/A' ? 'N/A' : String(player.name || '');
    const firstJoin = redacted ? 'redacted' : player.firstJoin === 'N/A' ? 'N/A' : player.firstJoin ? new Date(player.firstJoin) : '';
    const lastJoin = redacted ? 'redacted' : player.lastJoin === 'N/A' ? 'N/A' : player.lastJoin ? new Date(player.lastJoin) : '';
    const role = redacted ? 'redacted' : String(player.role || '');
    const playTimeHours = redacted ? 'redacted' : player.playHours === 'N/A' ? 'N/A' : player.playHours == null ? '' : Number(player.playHours);
    const balance = redacted ? 'redacted' : player.balance === 'N/A' ? 'N/A' : player.balance == null ? '' : Number(player.balance);
    const deaths = redacted ? 'redacted' : player.deaths === 'N/A' ? 'N/A' : player.deaths == null ? '' : Number(player.deaths);
    const playerKills = redacted ? 'redacted' : player.playerKills === 'N/A' ? 'N/A' : player.playerKills == null ? '' : Number(player.playerKills);
    const logoutLocation = redacted ? 'redacted' : String(player.logoutLocation || '');

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
      sheet.appendRow([uuid, name, firstJoin, lastJoin, role, playTimeHours, balance, deaths, playerKills, logoutLocation]);
    } else {
      sheet.getRange(foundRow, 2, 1, 9).setValues([[name, firstJoin, lastJoin, role, playTimeHours, balance, deaths, playerKills, logoutLocation]]);
    }

    return json({ ok: true, uuid: uuid, name: name, role: role, playTimeHours: playTimeHours, balance: balance, deaths: deaths, playerKills: playerKills, logoutLocation: logoutLocation });
  } catch (err) {
    return json({ ok: false, error: String(err) });
  }
}

function json(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
