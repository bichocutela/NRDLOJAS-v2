# Publicação segura de metadados Firebase

Este script é um publicador **manual** para preparar a migração. Não existe ainda
agendamento nem busca automática da Idever. As credenciais comerciais permanecem
no Supabase e não devem ser exportadas para este repositório.

1. Em ambiente administrativo confiável, instale as dependências com
   `cd scripts/firebase && npm install`.
2. Configure Application Default Credentials do projeto Firebase correto, sem
   versionar chaves privadas. Use uma identidade de serviço com acesso mínimo.
3. Obtenha um JSON validado das fontes oficiais. Nunca inclua tokens ou senhas.
4. Execute `node scripts/firebase/publish-snapshots.mjs stores lojas.json`
   ou `node scripts/firebase/publish-snapshots.mjs encartes encartes.json`
   a partir da raiz do repositório.
5. Confira os documentos `config/nordestao_stores` e
   `config/encartes_public` no Firestore antes de habilitar qualquer leitor.

O Firebase Admin SDK ignora as regras de cliente; por isso execute apenas
em infraestrutura privada com IAM restrito. A publicação automática requer
uma função de backend segura e ainda **não está implementada**.

Os APKs atuais continuam lendo Supabase. Não altere o roteamento até testar
instalações antigas, reinstalação, falha de rede e rollback.
