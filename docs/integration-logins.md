# Logins no Painel Mestre

Caminho: Painel Mestre → Configuração do aplicativo → Logins.

A tela separa Ofertas (reserva do Nordestão Pra Você), ACP e ACP Tech.
`Testar acesso atual` autentica e faz uma leitura de verificação, sem salvar.
`Alterar acesso` abre campos vazios; `Testar e salvar` valida os novos dados no
provedor antes de gravá-los no Vault. Uma falha no teste mantém o acesso salvo.
A senha existente nunca é devolvida ao aplicativo. Campos digitados ficam apenas
na memória da tela e são limpos ao salvar, cancelar ou sair.

É necessário instalar a versão que contém esta tela uma vez. As futuras trocas
de login não exigem outro APK. A ativação e a prioridade dos provedores ACP
continuam nas configurações de fontes de dados existentes.

## Servidor

- `nrd-integration-logins` valida criptograficamente o Firebase ID token do projeto
  NRD e exige a identidade Mestre antes de ler o corpo da solicitação.
- A lista retorna somente provedor, situação, revisão e data; não retorna login,
  CPF, senha, token do provedor ou dados da conta.
- Testes usam destinos fixos: portal oficial, ACP da loja configurada e ACP Tech
  da loja configurada. O cliente não pode escolher uma URL de autenticação.
- Ofertas testa a preferência atual sem mudá-la; ACP e ACP Tech validam uma
  leitura de categorias. Nenhum teste ativa ofertas ou altera dados comerciais.
- A gravação usa revisão esperada e transação; alterações concorrentes são
  rejeitadas para não sobrescrever uma atualização feita em outro aparelho.
- O Vault usa os nomes `nrd_portal_account`, `nrd_acp_legacy_account` e
  `nrd_acptech_account`. Não versionar valores reais nem registrar corpos ou
  headers de autenticação em logs.
- Os leitores e escritores SQL só concedem execução a `service_role`. Os leitores
  privados e o escritor privado também verificam a role do JWT; a tabela de
  revisões tem RLS e não concede acesso aos clientes.
- Até a primeira configuração do ACP/ACP Tech pelo painel, os respectivos
  acessos de ambiente existentes são preservados. A ausência verificada de uma
  configuração usa esse padrão; indisponibilidade do Vault não é interpretada
  como autorização para voltar silenciosamente a um acesso antigo.

## Renovação

O cache de credenciais dura 15 segundos por instância. As três integrações
comparam a revisão antes de reutilizar a sessão e autenticam novamente quando
ela muda. A rotina de Ofertas força uma leitura atual depois de adquirir a
trava global; a troca de credencial é serializada com essa aquisição.

Não é possível salvar a credencial de Ofertas durante uma consulta autenticada
em andamento. Havendo recuperação pendente de preferência, a senha da mesma
conta pode ser corrigida; trocar para outra conta exige resolver a recuperação
primeiro. Isso evita restaurar a preferência na conta errada.

## Testes

```sh
node --test supabase/functions/nrd-price-gateway/*.test.mjs supabase/functions/nrd-store-offers/*.test.mjs supabase/functions/nrd-integration-logins/*.test.mjs supabase/functions/_shared/integration-logins.test.mjs
```

Os testes cobrem bloqueio de usuários comuns, ausência de segredos nas respostas,
preservação em falha, revisão concorrente, cache e renovação das três sessões.
As asserções SQL de rotação usam credenciais sintéticas dentro de uma transação
revertida; não substituem os acessos de produção.
