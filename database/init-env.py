"""Generate an ignored local DB secret once; never replace existing credentials."""
from pathlib import Path
import secrets

path = Path(__file__).resolve().parents[2]/'backend/.env'
path.parent.mkdir(exist_ok=True)
try:
    with path.open('x', encoding='utf-8') as f:
        f.write('POSTGRES_PASSWORD=' + secrets.token_urlsafe(36) + '\n')
    print('Created backend/.env (secret not printed).')
except FileExistsError:
    print('Existing credentials retained.')
