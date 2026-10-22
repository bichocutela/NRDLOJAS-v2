# Histórico de cadastros

No Painel Mestre → Cadastros e Acessos, os documentos existentes em `restricted_access` são listados por login. Tocar no login expande as três permissões. Salvar atualiza o documento existente; desmarcar todas mantém o login sem abas liberadas. A opção global de liberar para todos continua prevalecendo sobre as permissões individuais.

A exclusão exige confirmação e chama `nrd-account-admin`. A função verifica assinatura, emissor, público e validade do ID token Firebase, exige a identidade Mestre ainda ativa e protege contas administrativas. Somente cadastros com documento de acesso e endereço correspondente em `usuarios.nrdlojas.com` podem ser excluídos. Primeiro revoga o documento, depois exclui o usuário Firebase Auth e por último remove o documento. Uma falha parcial mantém o acesso revogado e permite repetir a exclusão. A exclusão bem-sucedida permite reutilizar o nome em um novo cadastro, com outro UID.

A função reutiliza `FIREBASE_SERVICE_ACCOUNT` já configurado no Supabase; nenhum segredo administrativo é enviado ao app. A conta de serviço precisa de `firebaseauth.users.get`, `firebaseauth.users.delete` e leitura/atualização/exclusão de documentos Firestore. A CI verifica essas permissões na conta de serviço Firebase configurada no GitHub sem excluir usuários reais. As contas de serviço no GitHub e Supabase devem ter essas permissões.

Testes: `node --test supabase/functions/nrd-account-admin/handler.test.mjs`, `npm test --prefix tests/firestore`, e compilação assinada na CI. A validação de exclusão e falhas usa dependências simuladas; o teste de regras usa projeto Firebase de demonstração. Não são excluídas contas de produção nos testes.

O botão “Alterar dados”, ao lado de “Excluir”, abre login preenchido e nova senha mascarada. Deixar a senha em branco mantém a atual. O servidor valida o Mestre e a conta de destino, troca as credenciais no Firebase Auth e atualiza apenas o login no documento, preservando UID, permissões e dados vinculados. Login duplicado é rejeitado. Se o histórico falhar depois da troca, a tela mantém o formulário para repetir com o mesmo login. A senha nunca é gravada no Firestore. A função também requer `firebaseauth.users.update`.

## Um aparelho por cadastro

Cadastros `@usuarios.nrdlojas.com` reservam uma sessão por UID no servidor. O app guarda uma capacidade aleatória de 256 bits em `noBackupFilesDir`, sem transferência por backup; o servidor armazena só o hash. Claims usam precondições Firestore para que apenas um dispositivo vença. Fechar o app não libera a conta; reabrir no mesmo aparelho confirma a reserva. Outra instalação recebe “Esta conta está conectada em outro aparelho. Saia da conta no outro aparelho para entrar aqui.”

“Sair da conta” confirma a liberação no servidor antes de limpar a autenticação local. Se estiver sem conexão, mostra erro e mantém o login para tentar novamente. Contas Mestre/Admin não participam da reserva. A função requer também `datastore.entities.create`. O acesso ao gateway confere a capacidade e o `auth_time`, e os clientes não podem ler nem escrever `access_sessions`.

A regra passa a valer nas versões que incluem este fluxo. Versões anteriores não executam a reserva de login; as consultas restritas do gateway sem capacidade passam a ser negadas. A sessão não expira automaticamente. Desinstalar ou limpar os dados antes de sair perde a capacidade local e exige recuperação administrativa da reserva.
