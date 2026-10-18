# Firebase, Supabase e ACP: mapa confirmado em 18/10/2026

Auditoria do código e de metadados do Supabase. Nenhum documento de produção foi
apagado ou alterado. Não representa uma leitura completa do console Firebase.

| Responsabilidade | Caminho atual |
|---|---|
| Identidade e login | Firebase Auth; token validado pelas funções Supabase |
| Catálogo interno NRD | Firestore `products`, distinto do catálogo ACP |
| Histórico e restauração NRD | Firestore `catalog_history` e subcoleções de produtos |
| Escalas de trabalho | Firestore `work_schedules` |
| Aparência e configurações gerais | Firestore `config/appSettings`; inclui configurações usadas pela interface |
| Controle de acesso a preços/promoções | `PromotionControlClient` -> função `nrd-promotion-control` -> `nrd_control_documents` no Supabase |
| Validades manuais ACP | `config/acpOfferValidity` em `nrd_control_documents`; 4.274 campos na inspeção, versão 1069, atualizado em 17/10 |
| Cache/snapshot de promoções ACP | `nrd_promotion_cache` no Supabase; cache local Room no app |
| Preços e campanhas ACP Tech | API do ACP Tech, através de `nrd-price-gateway` |

## Migração e fallback

O marcador `promotion_control_migration.ready` está `true` no ambiente verificado.
Assim, o gateway resolve permissões em `nrd_control_documents`, em vez do fallback
Firestore. A implementação do fallback ainda existe no código para ambientes sem
migração. Firebase continua sendo o provedor de identidade nos dois caminhos.

Não confundir registros do catálogo NRD (`products` no Firestore) com os produtos
comerciais do ACP Tech (`chain_products` no backend do fornecedor).

## Efeito sobre os cartazes e a lista

As validades manuais antigas podem ocultar ofertas na lista ou gerar um cabeçalho
que contradiz o texto da campanha. A PR 194 preserva datas em cada `AcpOffer` e usa
a validade da origem antes das datas manuais, na lista e nos cartazes. Testes Android
e assembleDebug passaram antes da mesclagem. Campanha futura não é preço vigente.

## Estado do backend

A versão 47 do gateway contém as correções de paginação, campanhas e campos de
preço. A tentativa de descoberta global por campos comerciais, acrescentada na
PR 193, provocou erro 57014 (statement timeout) no ACP Tech. Mesmo reduzindo a
consulta para IDs e retirando contagem exata, a origem continuou falhando. Essa
varredura foi retirada de produção; campanhas e categorias promocionais continuam
sendo lidas. A sincronização voltou a concluir, sem retry pendente, às 17:20.

Consequentemente, a seção da auditoria anterior que descreve descoberta de ofertas
sem categoria não representa mais a implementação publicada. A descoberta completa
sem categoria permanece pendente e não deve ser declarada concluída.

## Próxima validação necessária

Comparar produtos reais por código/EAN, verificando cadastro, override da loja,
campanha distribuída, período e o resultado entregue à lista antes de abrir o detalhe.
As telas do Dumel mostram oferta de 19 a 21/10, embora a consulta tenha ocorrido em
18/10. Validar esse caso como oferta futura, separadamente dos preços vigentes.
