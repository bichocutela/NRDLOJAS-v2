# Reserva autenticada para Nossas Lojas → Ofertas

## Estado desta alteração

Base implementada e testada em simulação, **ainda não conectada ao `index.ts`
nem implantada em produção**. Não muda o APK, o ACP de Cidade Jardim ou as
publicações de documentos pelo Mestre.

`failover.mjs` recebe dois provedores de catálogos já normalizados, tenta o
principal, recorre à reserva, verifica novamente o principal após 60 segundos e
mantém cache separado por loja. Se ambos falharem, conserva o horário original
da consulta e marca o cache como desatualizado. O handler deve continuar usando
`activeOffers` antes de entregar o resultado, inclusive para cache antigo.

`portal-session.mjs` implementa o contrato de autenticação observado no código
do portal: POST `/api/auth/person`, campos `user` e `password` em FormData,
header `tokenAuth` e resposta `access_token`/`expiration`. As leituras usam
`tokenAuth` e `Authorization: Bearer …`. As sessões são reutilizadas no servidor,
com uma única autenticação concorrente por instância, renovação antecipada e
uma reconexão após 401. Uma falha de autenticação suspende novas tentativas por
cinco minutos. Não aceita termos, não ativa ofertas nem modifica a conta.

## O que impede a ativação

A revisão automática rejeitou a tentativa de provisionar a conta no Vault de
produção. Não houve gravação de credenciais por essa tentativa. Nenhuma senha,
CPF ou token pessoal consta destes arquivos, testes ou configurações do APK.

Antes de integrar e publicar:

1. Obter autorização específica para armazenar a conta no projeto Supabase
   `kkayksyzksexoarpfxyj`, em segredo separado e acessível somente pelo servidor.
   Preferir provisionamento pelo painel de segredos; não colocar credenciais em
   SQL versionado, logs, comentários de PR ou arquivos locais.
2. Validar a credencial de aplicação e o escopo exigidos pelo portal. O escopo
   usado pela consulta pública não deve ser presumido compatível com o portal.
3. Validar no servidor a autenticação e a paginação real de
   `/api/v2.0/Promotion/customer`, incluindo as datas e condições completas.
4. Confirmar a seleção de loja no contrato real. O portal observado altera a
   preferência por PUT `/api/store/preferential/{id}`; não se pode presumir que
   um parâmetro `storeId` seja respeitado pela consulta. Não publicar um catálogo
   sem confirmar a loja. Se a preferência for a única forma de seleção, será
   necessário proteger a troca com exclusão mútua entre instâncias do servidor
   e definir como preservar a preferência da conta. O cliente desta alteração
   deliberadamente não permite essa escrita ainda.
5. Normalizar exclusivamente dados comerciais permitidos; não repassar perfil,
   CPF, saldo, ativação, token ou outros dados pessoais da conta. Distinguir
   condições personalizadas de ofertas gerais antes de compartilhá-las.
6. Conectar os provedores ao handler existente, testar duas lojas diferentes,
   renovação real, indisponibilidade de cada fonte e retorno à principal, e só
   então implantar a função.

## Verificação local

```sh
node --test supabase/functions/nrd-store-offers/*.test.mjs
```

Os testes usam apenas valores fictícios. Não comprovam acesso de produção ou
compatibilidade de campos não observados na resposta real.
