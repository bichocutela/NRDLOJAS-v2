# Migração ACP para o servidor

A função `nrd-price-gateway` usa os Secrets Supabase existentes `NRD_PRICE_LOGIN` e
`NRD_PRICE_PASSWORD`. O Android envia somente seu ID token Firebase, quando logado.
Nenhuma senha ACP, cookie ou token ACP volta para o dispositivo.

## Etapas e verificação

1. Publicar `supabase/functions/nrd-price-gateway` no projeto `kkayksyzksexoarpfxyj`.
   `verify_jwt=false` é necessário porque a identidade é Firebase, verificada
   criptograficamente dentro da função, não uma sessão Supabase.
2. Executar `ACP server security`: testes simulados de permissões/contrato e leitura
   real de uma categoria. O teste usa GitHub OIDC restrito ao repositório, workflow,
   audience e refs conhecidos; só pode executar `health`, que responde `{ok:true}`.
3. Compilar/testar Android, inclusive release ofuscada. Não mudar Gradle 9.3.1,
   assinatura ou o mecanismo de versão existente. Só publicar após os checks verdes.
4. Conferir no aparelho: Mestre, login com preços liberados, login sem preços,
   liberar para todos, busca/EAN, Clube e detalhe de produto.

## Permissões

A mesma configuração Firestore do Painel Mestre é consultada a cada chamada:
`config/restricted_access.publicAccess` libera consultas para todos; do contrário,
`restricted_access/{uid}` exige `enabled=true` e `prices=true`. Mestre é reconhecido
pelo e-mail da identidade Firebase assinada. Não há um cadastro paralelo Supabase.
As leituras usam a conta de serviço `FIREBASE_SERVICE_ACCOUNT` já existente no
servidor. As autorizações são conferidas explicitamente pelos documentos do Mestre;
a conta de serviço e seu token nunca são enviados ao Android. Histórico e grupos de investigação
continuam exclusivos do Mestre. Grants não são cacheados pelo servidor.
Se o Firestore responder 429, a consulta é bloqueada e o app solicita nova tentativa;
nenhuma permissão é presumida e nenhum acesso direto à ACP é usado.

O app preserva seu cache comercial, mas valida acesso antes de devolver preço
cacheado. Uma autorização negada nunca aciona fallback direto para ACP.
Credenciais e cookies antigos do dispositivo são apagados na migração.

## Limites

A ofuscação R8 dificulta análise do código; não torna o endereço da função secreto.
As versões já instaladas continuam com o código antigo até atualizar. Depois de
confirmar a nova versão nos aparelhos, trocar a senha ACP invalida o segredo antigo;
esta mudança de senha não faz parte da migração automática.
A release produz `mapping.txt`, necessário para interpretar crashes ofuscados.
