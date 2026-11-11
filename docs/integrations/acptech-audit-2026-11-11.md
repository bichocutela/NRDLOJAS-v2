# ACP Tech → NRD: mapa técnico e proposta de integração

Inspeção em 11/11/2026. Conta da loja Nordestão 12, perfil Operador.

## Evidência e limites

- Login autenticado confirmado no portal e navegação de leitura em oito módulos.
- Produtos: 62.893 registros na tela. Esse número não significa quantidade de ofertas.
- Campanhas: 18 registros na aba “Campanhas Ativas”, incluindo campanhas futuras.
- Histórico de impressão: 295 registros na tela, com reimpressão, template, tamanho, produtos e status.
- Relatórios: filtros de período, usuário, template e tipo; abas Impressões, Produtos, Uso da IA e Comparativo; exportação Excel.
- Calendário: abas Central e Interno. Grupos: nenhum cadastrado na consulta. Comunicados: tela disponível.
- Nenhum produto, preço, usuário, campanha ou comunicado foi alterado. Nenhuma impressão, sincronização ou geração com IA foi solicitada.
- A sessão voltou ao login durante uma navegação. Um novo login seguro funcionou. A causa da saída não foi comprovada.
- Não apareceu “Lembrar acesso” nem confirmação de salvamento de senha; não afirmar que a credencial foi salva.

Este inventário lista referências literais encontradas no cliente web público atual. Não é documentação oficial OpenAPI, não garante cobertura de referências dinâmicas e não demonstra que a conta Operador pode executar todas as operações. Endpoints da Central não foram testados com a conta da loja. Não foram extraídos cookies, tokens ou chaves privadas do navegador.

Fonte pública: `https://acptech.com.br/assets/index-FfjXG-UK.js`.
Hash SHA-256: `ee78fe10cade670532ba64f001ef0266f5634aed11a0ce72a13b85fc8a55e00a`.
Inventário estruturado: [acptech-inventory-2026-11-11.json](acptech-inventory-2026-11-11.json).

## Rotas do ambiente da loja

Prefixo: `/lojas/:storeSlug`; loja validada: `nordestao12`. A raiz tem painel próprio. As rotas abaixo são declaradas no cliente; presença da rota não equivale a permissão.

| Rota relativa | Uso |
|---|---|
| `login` | Autenticação da loja |
| `produtos` | Catálogo, filtros e preços locais |
| `grupos-produtos` | Grupos reutilizáveis |
| `grupos-produtos/novo` | Criar grupo |
| `grupos-produtos/:id/editar` | Editar grupo |
| `campanhas` | Campanhas, vigência e origem |
| `campanhas/nova` | Criar campanha |
| `campanhas/:id/editar` | Editar campanha |
| `impressao` | Produtos, campanhas, impressão rápida, imagem e comunicado |
| `historico-impressao` | Histórico e reimpressão |
| `calendario-cartazes` | Agenda central e interna |
| `comunicados` | Comunicados da loja |
| `alertas` | Alertas da rede |
| `relatorios` | Indicadores e exportações |
| `logs` | Auditoria |
| `falhas` | Falhas do sistema |
| `usuarios` | Administração de usuários |
| `configuracoes` | Configuração da loja |
| `treinamentos` | Conteúdo de aprendizado |
| `ajuda` | Ajuda do portal |
| `novidades` | Novidades do sistema |
| `meu-perfil` | Perfil e acesso |

## Rotas da Central

Prefixo `/central/:chainSlug`. Exigem identidade e permissões da Central; não assumir acesso pela conta Nordestão 12. Raiz com painel, mais as rotas declaradas:

- `login`
- `templates/new`
- `templates/:id`
- `lojas`
- `etiquetas-minew`
- `etiquetas-minew/loja/:storeId`
- `templates`
- `categorias-template`
- `escalas-template`
- `variantes-template`
- `campanhas`
- `catalogo-produtos`
- `unidades`
- `unidades-conteudo`
- `grupos-produtos`
- `grupos-produtos/novo`
- `grupos-produtos/:id/editar`
- `tipos-embalagem`
- `familias-produtos`
- `campanhas/nova`
- `campanhas/:id`
- `usuarios`
- `logs`
- `falhas`
- `configuracoes`
- `treinamentos`
- `ajuda`
- `impressao`
- `historico-impressao`
- `alertas`
- `calendario-cartazes`
- `relatorios`
- `novidades`
- `comunicados`
- `meu-perfil`

## Base e contratos de dados

Origem do backend identificada no cliente: `https://lolnlmlnvspipzxatgvj.supabase.co`.

| Tipo | Padrão de endpoint | Observação |
|---|---|---|
| REST de tabelas | `/rest/v1/<recurso>` | Filtros de rede/loja e autorização no servidor são obrigatórios |
| Funções SQL RPC | `/rest/v1/rpc/<nome>` | Normalmente POST com parâmetros; confirmar contrato por operação |
| Edge Functions | `/functions/v1/<nome>` | Corpos/action distintos; existência não garante permissão |
| Autenticação | `/auth/v1/...` | Sessão gerenciada pelo cliente Supabase; não expor tokens no NRD |
| Imagens públicas | `/storage/v1/object/public/<bucket>/<caminho>` | Somente objetos publicados; não adivinhar caminhos |

### Funções de aplicação encontradas

| Função | Uso observado / limite |
|---|---|
| `login-context` | Resolver contexto de loja ou central |
| `store-auth` | Login e contexto da loja; ações atuais `login`, `bootstrap`, `private-context`, `session-status`; também há referência a alteração de senha |
| `central-auth` | Autenticação da Central; mesmas ações de contexto/sessão observadas |
| `device-request` | Solicitar registro de aparelho com chave pública |
| `device-status` | Consultar estado do aparelho |
| `device-challenge` | Obter desafio de validação |
| `device-verify` | Validar resposta ao desafio |
| `device-session-status` | Consultar sessão de aparelho |
| `spellcheck-text` | Correção de descrição/característica com IA, sujeita a limite mensal |
| `generate-banner-style` | Geração de estilo de faixa, sujeita a permissões/consumo |
| `minew-esl-central` | Integração de etiquetas eletrônicas; recurso da Central |

As funções de aparelho devem ser respeitadas. Não aprovar dispositivos, gerar novas credenciais ou contornar desafios como parte de uma auditoria.

### RPCs encontradas

- `central_dashboard_summary`
- `central_templates_page`
- `change_center_user_password`
- `count_chain_products`
- `ensure_units_exist`
- `get_ai_usage_chain_direct_current_month`
- `get_ai_usage_chain_summary`
- `get_ai_usage_current_month`
- `get_chain_logo_by_id`
- `get_chain_settings_for_store`
- `get_institutional_presence`
- `get_internal_products_by_barcodes`
- `search_chain_products`

`change_center_user_password` altera credenciais; `ensure_units_exist` pode criar unidades. Não tratar todo RPC como leitura. Funções de IA podem consumir cota. Dashboard/templates centrais exigem autorização própria.

### Recursos REST encontrados

- `ai_usage_logs`
- `banner_styles`
- `campaign_distributions`
- `campaign_items`
- `campaign_template_products`
- `campaign_templates`
- `campaigns`
- `center_audit_logs`
- `center_profiles`
- `center_users`
- `chain_alert_reads`
- `chain_alert_targets`
- `chain_alerts`
- `chain_category_font_settings`
- `chain_poster_calendar`
- `chain_poster_calendar_targets`
- `chain_product_barcodes`
- `chain_product_family_links`
- `chain_product_group_items`
- `chain_product_groups`
- `chain_products`
- `chain_settings`
- `chain_statements`
- `chain_template_settings`
- `chains`
- `content_units`
- `fonts`
- `internal_news`
- `internal_news_chains`
- `internal_news_reads`
- `internal_news_stores`
- `internal_training_attachments`
- `internal_training_screen_tags`
- `internal_trainings`
- `package_types`
- `print_log_items`
- `print_logs`
- `product_categories`
- `product_families`
- `statements`
- `store_audit_logs`
- `store_group_members`
- `store_groups`
- `store_poster_calendar`
- `store_product_price_overrides`
- `store_profiles`
- `store_scale_visibility`
- `store_settings`
- `store_users`
- `stores`
- `system_failures`
- `template_categories`
- `template_groups`
- `template_scales`
- `template_store_overrides`
- `template_store_visibility`
- `template_types`
- `template_variants`
- `templates`
- `units`

Os 60 nomes acima foram encontrados como chamadas literais do cliente principal (`ye.from`). Relações dentro de selects e nomes dinâmicos podem acrescentar recursos. A presença de uma tabela não autoriza varrer seus dados. Usuários, auditoria e cadastros ficam em fluxos administrativos restritos.

### Storage

Buckets literais do cliente: `platform`, `product-images`, `templates`. Há referências a imagens de produto e materiais de impressão. Não publicar novamente materiais internos sem definir seu público de destino.

## Diferenças relevantes em relação ao adaptador do NRD

1. `supabase/functions/nrd-price-gateway/acptech-client.mjs` ainda inicia com `store-auth`/`resolve_email` e depois `/auth/v1/token?grant_type=password`. O cliente atual usa `store-auth` com `action: login` e recebe uma sessão. A ausência de `resolve_email` no bundle não prova que o servidor removeu compatibilidade. Validar antes de substituir.
2. O bootstrap atual recebe/retorna `sessionId`/`session_id`; o portal consulta `session-status` periodicamente. O adaptador antigo não acompanha esse contrato. Testar se logins concorrentes do portal/gateway invalidam sessões antes de atribuir a isso as saídas observadas.
3. O cliente atual inclui validação de aparelho com chave pública e desafio assinado. Não copiar chave privada do navegador para o app ou remover a proteção. Confirmar com o fornecedor o fluxo suportado de integração servidor a servidor.
4. O NRD já tem busca `search_chain_products`, leitura de preços locais, campanhas/itens, EAN alternativo e categorias. Isso não cobre impressão, relatórios, calendário, comunicados e administração.
5. Rotas genéricas do gateway antigo que representam histórico/grupos não devem retornar “vazio” como se tivessem sido implementadas. Criar adaptadores explícitos ou informar recurso indisponível.
6. Não misturar campanha futura, central não distribuída ou oferta vencida com oferta vigente da loja. Verificar início, fim, origem, distribuição e preço efetivo.
7. Renderização de cartazes ocorre também no navegador: dados de templates não equivalem a uma API pronta de PDF. É necessário reproduzir dimensões em mm, fontes, escalas, variantes e condições, e comparar a saída visual.

## Como organizar no NRD

| Área NRD | Conteúdo | Apresentação proposta |
|---|---|---|
| Consultar Preços | Catálogo, EAN, preço local, Clube/NordCard, atacado e validade | Busca por leitor/código/nome, foto, condições em blocos, horário da consulta |
| Promoções | Campanhas distribuídas e itens vigentes | Abas Vigentes/Em breve/Encerradas, filtros e descontos ordenáveis |
| Cartazes | Templates, escalas, grupos e prévia | Galeria com miniaturas; sequência Produto → Modelo → Tamanho → Prévia → PDF/Imprimir |
| Impressão rápida | Lista colada ou Excel/CSV | Importação com revisão de descrição, preço e quantidade antes de imprimir |
| Histórico | Impressões e itens | Lista pesquisável, filtros por data/modelo/status e reimpressão revisável |
| Agenda | Calendário central e interno | Calendário mensal e lista “Hoje”, com origem claramente indicada |
| Comunicados | Comunicados e alertas | Caixa de entrada interna com público definido |
| Relatórios | Impressões, produtos, sucesso e período | Indicadores compactos e exportação, exclusivos do público autorizado |
| Treinamento | Materiais e anexos | Biblioteca separada da simulação de caixa existente no NRD |
| Mestre | Configuração, perfis e permissões | Área administrativa separada das ofertas públicas |

Direção visual: manter os temas do NRD, cartões com hierarquia consistente, preços em destaque, textos legíveis, ações que se reorganizam em telas estreitas e apenas o sino para notificações, conforme preferência do usuário. Logos oficiais permanecem intactas. Evitar colocar toda a administração no menu público.

## Sequência de implementação e critérios de aceite

1. **Sessão e compatibilidade**: validar login atual/renovação/sessões e obter fluxo suportado de aparelho; testar expiração e login concorrente sem ampliar permissões.
2. **Paridade comercial**: comparar produtos reais de preço normal, De/Por, Clube/NordCard, leve/pague, atacado, cashback e segunda unidade; campanha futura e vencida não podem aparecer como vigentes.
3. **Leituras adicionais**: histórico, grupos, calendário e comunicados com escopo de loja, paginação e DTOs sem dados administrativos desnecessários.
4. **Cartazes**: renderizador compatível, prévia e PDF com medidas verificadas; nenhuma impressão disparada sem ação clara do usuário.
5. **Relatórios e gestão**: apenas após validar permissões. Dados de usuários, senhas, alterações de preço e publicação de campanhas exigem rotas administrativas específicas.

Credenciais de integração ficam no servidor. O APK não recebe senha de serviço nem service role. Reutilizar snapshots e paginação para reduzir consumo; não aumentar consultas por aparelho nem criar novos projetos como solução de cache. Esta auditoria não altera a separação Firebase/Supabase já definida para o NRD.

## Estado da entrega

Mapa de rotas e referências de API concluído no escopo do bundle e das telas verificadas. Nenhuma nova integração funcional foi publicada nesta auditoria. Contratos autenticados de todas as operações, permissões da Central e renderização de cartazes continuam pendentes de validação/implementação. Não anunciar “todas as APIs integradas” com base apenas neste inventário.
