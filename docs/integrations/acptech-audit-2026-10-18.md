# ACP Tech: auditoria da integração NRD em 18/10/2026

## Evidência e limites

Inspeção estática do JavaScript servido publicamente pelo portal em
https://acptech.com.br/assets/index-ixlPZJNd.js (4.457.151 caracteres), referenciado
por https://acptech.com.br/lojas/nordestao-12/login. Sem extrair sessão ou enviar
credenciais a endpoints durante esta inspeção. As chamadas abaixo são usadas pelo
cliente oficial; isso não equivale a uma API pública com contrato de estabilidade.
A comparação autenticada de todos os produtos da loja ainda não foi concluída.
Não interpretar filtros vazios como prova de ausência de ofertas.

## Fontes e precedência comprovadas no cliente oficial

| Informação | Fonte e comportamento observado |
|---|---|
| Catálogo | `chain_products`, escopo `chain_id`, `deleted_at is null` |
| Busca estruturada | RPC `search_chain_products`: código, prefixo EAN, descrição, categoria, IDs, códigos e EANs em lote, limite e offset |
| Contagem | RPC `count_chain_products`, com rede, categoria e exclusão de apagados |
| Preços da loja | `store_product_price_overrides`, escopo `store_id`, vínculo `chain_product_id` |
| Campos de override na impressão | `price`, `previous_price`, `wholesale_price`, `wholesale_quantity`, `club_price`, `valid_until`, `installments`; somente valores não nulos substituem o cadastro; zero é explícito |
| Ofertas | Campos do produto e `campaign_items`; preço anterior, clube, leve/pague, cashback, segunda unidade e validade são campos distintos |
| Campanhas da loja | Lista oficial filtra `campaigns.store_id`; itens são lidos por `campaign_id` |
| Campanhas da central | Separadas por rede e `store_id is null`; a distribuição cria/atualiza cópias para cada loja e registra `campaign_distributions` |
| EAN alternativo | Seleção de produto tenta `chain_products.barcode`; se ausente procura `chain_product_barcodes` e resolve o produto por ID |
| Unidades/embalagens | Relações `units` e `package_types`, via `unit_id` e `package_type_id` |
| Famílias | `chain_product_family_links` e `product_families`, independentes de categoria |

Clube é `club_price`; Leve/Pague é `leve_quantity` + `pague_quantity`.
O renderizador oficial calcula o equivalente unitário como preço / leve * pague.
De/Por depende do par `previous_price` e `price`. Categoria sozinha não contém os
valores comerciais e pode não estar vinculada ao produto.

## Correção adicional implementada no PR 192

A varredura de ofertas passa a incluir candidatos com campos comerciais positivos
no cadastro da rede e nos overrides da loja, sem exigir categoria promocional.
O filtro final usa os valores efetivos da loja, preserva zeros que removem uma
condição e exclui regras vencidas. Consulta paginada, limitada e restrita à rede/loja.
Um teste cobre Clube, De/Por, Leve/Pague, oferta exclusiva da loja, remoção por zero,
produto comum e oferta vencida. Suíte Node: 87 testes aprovados.

## Lacunas que ainda precisam de validação ou implementação

1. Confirmar com produtos reais a precedência entre campanha distribuída da loja,
   campanha central e cadastro. O adaptador atual inclui campanhas centrais e da
   loja; o portal mantém fluxos distintos. Não assumir que toda campanha central
   está autorizada para toda loja. A escolha do menor De/Por no adaptador não é
   precedência comprovada pelo portal.
2. Resolver nomes das unidades e embalagens; não depender de uma propriedade
   textual `unit` inexistente quando o cadastro só fornece `unit_id`.
3. Cobrir EAN alternativo no NRD e associar campanhas pela identidade do produto,
   evitando correspondência por descrição.
4. Medir quantidade final por família com dados autenticados da loja e comparar
   amostras de cada tipo e datas. Os 490 registros observados no snapshot anterior
   não significam 490 ofertas de cada família.
5. Confirmar contrato e estabilidade dos endpoints com ACP Tech antes de tratá-los
   como integração oficial suportada. Não foi encontrada documentação pública de
   uma API agregada de todas as ofertas nesta inspeção.

## Reaproveitamento recomendado

Prioridade imediata: catálogo e busca RPC, preços locais, condições e datas de
campanha, EANs alternativos, imagens, unidades e embalagens. Depois: famílias de
produtos, limite por CPF, atacado, conteúdo/preço por unidade, cashback e segunda
unidade, sempre mantendo cada condição explícita na interface.

O portal também oferece modelos de cartazes, escalas, logs de impressão, calendário,
comunicados e treinamentos. São módulos separados; existência no bundle não prova
permissão da conta nem justifica copiá-los ao NRD. Requerem demanda e validação de
acesso próprias. Nenhuma escrita foi feita no ACP Tech.

## Publicação

Alterações permanecem no PR 192. A compilação Android e os testes da revisão
anterior passaram no GitHub Actions; a correção adicional acima foi validada pela
suíte Node. Nenhuma publicação de produção ou merge foi feita nesta auditoria.

## Atualização após validação em produção

A descoberta global por campos comerciais foi retirada na versão 47 porque o ACP Tech
retornou statement timeout (57014). A conclusão automática de ofertas sem categoria
continua pendente. Consulte `firebase-acp-map-2026-10-18.md` para o estado atual.
