/**
 * Avianto · copias de seguridad en Google Drive.
 *
 * Publicá este script desde la cuenta de Google donde querés guardar las copias
 * (ver «Vincular Google Drive» en Administración). Avianto le envía cada copia y el
 * script la guarda en la carpeta «Avianto Backups» de tu Drive.
 *
 * La clave de abajo la genera Avianto. No la compartas: quien la tenga puede subir
 * archivos a esa carpeta. Si el script cambia, volvé a implementarlo.
 */
const TOKEN = '__AVIANTO_TOKEN__';
const FOLDER_NAME = 'Avianto Backups';
const FOLDER_KEY = 'FOLDER_ID';
const BACKUP_NAME = /^avianto-\d{8}T\d{6}Z\.sql\.gz$/;
const MAX_BYTES = 35 * 1024 * 1024;

/** Ejecutala una vez desde el editor (elegí «probar» y tocá Ejecutar) para dar los permisos de Drive. */
function probar() {
  return carpeta_().getName();
}

/** Punto de entrada que llama Avianto. Siempre responde JSON con «ok». */
function doPost(e) {
  try {
    const pedido = JSON.parse(e.postData.contents);
    if (pedido.token !== TOKEN) {
      return respuesta_({ ok: false, code: 'TOKEN', error: 'La clave no coincide con la de Avianto. Copiá el script de nuevo desde Administración.' });
    }
    const carpeta = carpeta_();
    if (pedido.action === 'ping') return respuesta_(estado_(carpeta));
    if (pedido.action === 'upload') return respuesta_(subir_(carpeta, pedido));
    return respuesta_({ ok: false, code: 'ACTION', error: 'Acción desconocida.' });
  } catch (err) {
    return respuesta_({ ok: false, code: 'ERROR', error: String(err) });
  }
}

function subir_(carpeta, pedido) {
  if (!BACKUP_NAME.test(pedido.name)) return { ok: false, code: 'NAME', error: 'Nombre de copia no válido.' };
  const bytes = Utilities.base64Decode(pedido.dataBase64);
  if (bytes.length > MAX_BYTES) return { ok: false, code: 'SIZE', error: 'La copia es demasiado grande para Google Apps Script.' };
  const archivo = carpeta.createFile(Utilities.newBlob(bytes, 'application/gzip', pedido.name));
  podar_(carpeta, Number(pedido.retentionDays), Number(pedido.keepNewest));
  return Object.assign({ ok: true, fileId: archivo.getId() }, estado_(carpeta));
}

/** Manda a la papelera las copias vencidas y conserva siempre las más nuevas. Solo toca archivos avianto-*. */
function podar_(carpeta, dias, conservar) {
  const limite = new Date(Date.now() - dias * 24 * 60 * 60 * 1000);
  const copias = [];
  const it = carpeta.getFiles();
  while (it.hasNext()) {
    const archivo = it.next();
    if (BACKUP_NAME.test(archivo.getName())) copias.push(archivo);
  }
  // El nombre lleva la fecha con formato ordenable: el más nuevo queda primero.
  copias.sort((a, b) => (a.getName() < b.getName() ? 1 : -1));
  copias.slice(conservar).forEach((archivo) => {
    if (archivo.getDateCreated() < limite) archivo.setTrashed(true);
  });
}

function carpeta_() {
  const propiedades = PropertiesService.getScriptProperties();
  const id = propiedades.getProperty(FOLDER_KEY);
  if (id) {
    try {
      const existente = DriveApp.getFolderById(id);
      if (!existente.isTrashed()) return existente;
    } catch (err) {
      // La carpeta fue borrada o ya no es accesible: se crea otra.
    }
  }
  const nueva = DriveApp.createFolder(FOLDER_NAME);
  propiedades.setProperty(FOLDER_KEY, nueva.getId());
  return nueva;
}

function estado_(carpeta) {
  return { ok: true, folderName: carpeta.getName(), account: Session.getEffectiveUser().getEmail() };
}

function respuesta_(datos) {
  return ContentService.createTextOutput(JSON.stringify(datos)).setMimeType(ContentService.MimeType.JSON);
}
