/* Documents belong to the selected profile, independently of the current chat. */
(() => {
  let state = null;
  const el = id => document.getElementById(id);
  const active = () => S.activeProfile || 'default';
  const fail = error => { el('documentsError').textContent = error.message || String(error); };
  const child = name => state.path === '.' ? name : `${state.path}/${name}`;
  const query = (action, path = state.path) => 'api/documents?' + new URLSearchParams({action, space: state.space, path});
  function checkProfile() {
    if (!state || active() !== state.profile) throw new Error('Le profil a changé. Fermez et rouvrez les fichiers.');
  }
  async function guarded(work) {
    if (state?.busy) return;
    state.busy = true; el('documentsError').textContent = '';
    el('documentsBody').setAttribute('aria-busy', 'true');
    try { checkProfile(); await work(); } catch (error) { fail(error); }
    finally { if (state) state.busy = false; el('documentsBody').setAttribute('aria-busy', 'false'); }
  }
  function button(label, action) {
    const b = document.createElement('button'); b.type = 'button'; b.className = 'btn secondary'; b.textContent = label;
    b.onclick = () => guarded(action); return b;
  }
  async function load() {
    const data = await api(query('list')); checkProfile();
    el('documentsPath').textContent = state.path;
    const list = el('documentsEntries'); list.replaceChildren();
    if (!data.entries.length) list.textContent = 'Ce dossier est vide.';
    for (const entry of data.entries) {
      const row = document.createElement('div'); row.className = 'document-row';
      const path = child(entry.name);
      row.append(button((entry.is_dir ? '📁 ' : '') + entry.name, async () => {
        if (entry.is_dir) { state.path = path; await load(); }
        else {
          const doc = await api(query('read', path)); checkProfile();
          state.editor = {path, revision: doc.revision};
          el('documentsText').value = doc.content; el('documentsEditor').hidden = false;
          el('documentsEditing').textContent = path;
        }
      }));
      if (!entry.is_dir) {
        row.append(button('Télécharger', async () => {
          checkProfile(); const a = document.createElement('a'); a.href = query('download', path); a.download = entry.name;
          document.body.append(a); a.click(); a.remove();
        }));
        row.append(button('Renommer', async () => {
          const {revision} = await api(query('revision', path)); checkProfile();
          const name = prompt('Nouveau nom du fichier', entry.name); if (!name) return;
          checkProfile(); await mutate('rename', path, {name, revision});
        }));
        row.append(button('Supprimer', async () => {
          const {revision} = await api(query('revision', path)); checkProfile();
          if (!confirm(`Supprimer définitivement ${entry.name} ?`)) return;
          checkProfile(); await mutate('delete', path, {revision});
        }));
      }
      list.append(row);
    }
  }
  async function mutate(action, path, fields) {
    checkProfile();
    await api('/api/documents', {method: 'POST', body: JSON.stringify({action, space: state.space, path, ...fields})});
    checkProfile(); await load();
  }
  window.openDocuments = async () => {
    const dialog = el('documentsDialog');
    if (dialog.open) return;
    state = {profile: active(), space: '', path: '.', busy: false, editor: null};
    el('documentsProfile').textContent = state.profile;
    el('documentsEntries').replaceChildren(); el('documentsSpaces').replaceChildren(); el('documentsEditor').hidden = true;
    dialog.showModal();
    await guarded(async () => {
      const {spaces} = await api('/api/documents'); checkProfile();
      for (const space of spaces) {
        const option = document.createElement('option'); option.value = space.id; option.textContent = space.name;
        el('documentsSpaces').append(option);
      }
      state.space = spaces[0]?.id || '';
      if (state.space) await load(); else el('documentsEntries').textContent = 'Aucun espace accessible pour ce profil.';
    });
  };
  window.addEventListener('DOMContentLoaded', () => {
    el('documentsSpaces').onchange = () => guarded(async () => {
      state.space = el('documentsSpaces').value; state.path = '.'; state.editor = null; el('documentsEditor').hidden = true; await load();
    });
    el('documentsUp').onclick = () => guarded(async () => {
      state.path = state.path.includes('/') ? state.path.slice(0, state.path.lastIndexOf('/')) : '.'; await load();
    });
    el('documentsRefresh').onclick = () => guarded(load);
    el('documentsCreate').onclick = () => guarded(async () => {
      if (!state.space) return;
      const name = prompt('Nom du fichier texte'); if (!name) return;
      if (name.includes('/') || name.includes('\\') || name === '.' || name === '..') throw new Error('Nom de fichier invalide');
      await mutate('create', child(name), {content: ''});
    });
    el('documentsSave').onclick = () => guarded(async () => {
      if (!state.editor) return;
      await mutate('save', state.editor.path, {content: el('documentsText').value, revision: state.editor.revision});
      state.editor = null; el('documentsEditor').hidden = true;
    });
    el('documentsCancelEdit').onclick = () => { if (!state.busy) { state.editor = null; el('documentsEditor').hidden = true; } };
    el('documentsClose').onclick = () => { if (!state.busy) el('documentsDialog').close(); };
    el('documentsDialog').addEventListener('cancel', event => { if (state?.busy) event.preventDefault(); });
  });
})();
