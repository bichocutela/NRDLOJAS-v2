# Gateway interno — bloco 1 (piloto)
Rota nova e isolada; nenhuma alteração Android, Gradle, build, login, regras, bucket ou função existente.
Não migrar clientes para esta rota ainda.

## Configuração privada
Cadastrar no Supabase Edge Function secrets: NRD_PRICE_LOGIN, NRD_PRICE_PASSWORD e NRD_GATEWAY_PILOT_UID (UID Firebase do Mestre autorizado). Nunca copiar credenciais para Git, logs ou APK.
Deploy como nrd-price-pilot com verify_jwt=false: autenticação é Firebase RS256 com audience/issuer fixos, expiração e UID piloto verificados no handler.
O publishable/anon key não concede acesso. Falta de UID nega todos; falta de credenciais retorna 503.
Não reutilizar claims de user_metadata Supabase.

## Contrato piloto
POST com x-firebase-token e JSON {query,field,page}. field=code/barCode/description. Até 20 produtos, página 0..100.
Resposta {products,page} normalizada por lista de campos escalares; não é contrato Android final.
Somente Product/all via GET após autenticação NextAuth server-side. Hosts fixos; redirects recusados; cookies isolados por requisição.
Nenhuma escrita no fornecedor, proxy arbitrário ou envio de credenciais externas ao cliente.
Esta rota destina-se exclusivamente ao piloto Mestre: não liberar publicamente sem proteção por instalação/usuário e limitação persistente de requisições.

## Verificação
node --test supabase/functions/nrd-price-pilot/handler.test.mjs
7 testes locais com fornecedor simulado passaram. Em 01/10/2026 o piloto foi publicado (versão 2): chamada sem autenticação 401, token inválido 401 e token Firebase Mestre válido 200 com 20 produtos para busca por descrição. Credenciais ficaram somente nos secrets. Nenhuma alteração no Android; isso não valida ainda equivalência completa das ofertas, categorias, unidades, paginação ou acesso dos usuários comuns.

## Critérios para bloco 2
Confirmar envelope real de Product/all e mapear todos os campos comerciais (Clube, atacado, leve/pague, cashback, validade). A lista atual é provisória.
Validar permissão de uso, conectividade e secrets com consulta somente leitura.
Implementar rate limit persistente e sessão/cache servidor antes de distribuir (piloto faz login por requisição).
Definir acesso de usuários que hoje consultam preços sem login.
Integrar atrás de chave desativada; testar preços, buscas, leitor, ofertas, sincronização e indisponibilidade.
Migrar Nossa Gente em bloco independente, com sessão por usuário, expiração e logout, sem credencial compartilhada.
Somente depois remover credenciais/hosts do APK e rotacionar credencial ACP antiga. APKs antigos permanecem expostos até a rotação.
Rollback antes da remoção: desativar migração; após remoção não restaurar credenciais no APK.

## Bloco de contrato e proteção (01/10/2026)
Piloto versão 3 publicado: unidades, categorias do produto, família, embalagem, descrições auxiliares, campos comerciais e metadados de paginação projetados explicitamente. Páginas 0 e 1 da busca real retornaram 200, 20 produtos cada e totalPages=4.
11 testes locais passaram. Rate limit persistente por UID piloto: 60/minuto; bloqueio da 61ª validado em transação revertida. Falha no controle bloqueia a consulta; usuários anon/authenticated não executam o RPC.
Migração aplicada: internal_price_pilot_rate_limit; rate-limit.sql registra o DDL aplicado (não executar novamente sem consultar histórico).
Tabela privada com RLS sem políticas é intencional: usuários comuns não acessam; somente service_role tem grants. O advisor informativo está documentado em https://supabase.com/docs/guides/database/database-linter?lint=0008_rls_enabled_no_policy.
Ainda faltam rotas de categorias, campanhas e integração, cache/sessão de servidor e autenticação dos usuários comuns; a consulta piloto não substitui todos os caminhos de AcpApi. Android e Nossa Gente permanecem sem migração.
