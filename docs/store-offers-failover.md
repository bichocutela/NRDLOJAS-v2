# Reserva autenticada — Nossas Lojas → Ofertas

## Funcionamento

A função `nrd-store-offers` mantém a consulta pública original como principal.
Em erro HTTP, timeout ou catálogo inválido, recorre ao portal autenticado. Testa
novamente a principal após 60 segundos e volta a ela quando responder. Reutiliza
um cache de reserva ainda válido durante essas verificações, evitando repetir
o login e a consulta autenticada a cada tentativa.

Os catálogos ficam separados pelo ID oficial da loja, em memória e na tabela
`nrd_store_offer_cache`, acessível somente pelo servidor. O cache dura cinco
minutos; se ambas as fontes falharem, preserva a data da consulta anterior e
retorna `stale:true`. `activeOffers` filtra a vigência em toda resposta, inclusive
no cache desatualizado. Respostas vazias válidas não ressuscitam ofertas antigas.

Isso não modifica o ACP de Cidade Jardim nem as ofertas de documentos publicadas
pelo Mestre na aba Promoções. O APK continua acessando o mesmo endpoint, sem
receber login, senha, token ou informações da conta de integração.

## Contrato do portal verificado

Origem: `https://pravoce.nordestao.com.br`.
API: `https://idevercustomerportalapiprod.azurewebsites.net/api`.

- Autenticação: POST `/auth/person`, FormData `user`/`password`, headers Origin e
  Referer da origem oficial. Exige `isSuccess:true` e lê `data.access_token` e
  `data.expiration`. A expiração observada é numérica; também aceita ISO e epoch
  em segundos. Não confundir com duração em segundos.
- O site de produção identifica o tenant pela origem. Não usar a credencial da
  configuração localhost ou da API pública como `tokenAuth` nesse fluxo.
- Leituras autenticadas usam `Authorization: Bearer …`, Origin e Referer.
- GET `/v2.0/Promotion/customer` é a vitrine paginada do site. A reserva utiliza
  GET `/promotion/customer`, que retorna as campanhas completas em uma lista,
  com itens, condições e vigências, sem extrair preços de textos da interface.
- GET `/store/preferential` informa a loja efetiva; PUT
  `/store/preferential/{id}` seleciona outra loja.

O cliente reutiliza a sessão, renova antes da expiração e tenta uma reconexão
após 401. Se o login falhar ou exigir atualização de cadastro/termos, suspende
novos logins por cinco minutos e usa o tratamento de cache. Não aceita termos,
não ativa ofertas, não dá likes, não altera cadastro e não consulta saldo.

## Seleção de loja e concorrência

Como o portal utiliza a preferência da conta, a reserva adquire uma trava
compartilhada no banco antes de consultar. Registra a preferência original antes
de trocar, verifica a loja antes e depois da leitura e restaura a preferência
antes de devolver um catálogo de outra unidade. Se o usuário mudar a preferência
externamente durante a consulta, não sobrescreve essa nova escolha e rejeita o
resultado que possa pertencer a outra loja.

A trava vale entre instâncias diferentes da função. O contexto de restauração
fica no banco; se um worker morrer, a próxima consulta autenticada recupera a
preferência após a expiração da trava, que dura dez minutos. Uma restauração que
falha impede a publicação daquele resultado e mantém o contexto para recuperação.
O tempo de trabalho reserva uma janela separada para a restauração.

Dados pessoais e estados de ativação não fazem parte do catálogo entregue.
As condições avisam que cada cliente precisa conferir sua elegibilidade e ativar
ofertas na própria conta. Imagens são as URLs oficiais retornadas no catálogo,
sem inventar URLs ou substituir por outro produto.

## Configuração e operação

O proprietário autorizou o armazenamento da conta no Vault do projeto NRD.
O segredo separado chama-se `nrd_portal_account`, com campos `user` e `password`.
Seu conteúdo deve ser provisionado/rotacionado fora dos arquivos versionados.
Nunca colocar valores em código, APK, testes, logs ou comentários de PR.

`public.nrd_portal_account()` é invoker-only e somente `service_role` pode
executá-la. O leitor privado também verifica a role do JWT. As tabelas de cache
e de trava possuem RLS e não concedem acesso a `anon` ou `authenticated`.
As ausências de políticas nessas tabelas são intencionais: acesso apenas pelo
servidor. Logs registram códigos de erro e status HTTP, nunca corpos de login,
headers de autenticação ou valores de segredos.

Após trocar o segredo, reimplantar a função descarta a sessão em memória para
usar as novas credenciais. Para desligar a reserva, remover o segredo separado
e reimplantar a função; a consulta principal continua independente.

## Validação

```sh
node --test supabase/functions/nrd-store-offers/*.test.mjs
```

Os testes cobrem alternância, recuperação da principal, cache compartilhado,
isolamento por loja, validade, reconexão limitada, autenticação de produção,
restauração da preferência e recuperação de worker interrompido.

Em 24/10/2026, uma função de teste isolada validou o acesso autenticado com a
principal simulada como indisponível: Cidade Jardim retornou 124 ofertas e Ponta
Negra retornou 145. A fonte principal de produção não foi desabilitada. O teste
verificou a restauração da preferência; a função temporária foi encerrada e
passou a responder 410 sem acesso à conta. Os números variam com as campanhas.
