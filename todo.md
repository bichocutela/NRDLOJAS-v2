# TODO - NRD LOJAS

## Aba Consultar Produtos — paridade comercial ACP
- [x] Definir contrato unificado para preço normal, De/Por, Clube, cashback, Leve/Pague, segunda unidade, atacado, vigência e limites.
- [x] Mapear fontes ACP e preservar ofertas simultâneas, sem escolher apenas uma promoção.
- [x] Normalizar percentuais, valores, datas, unidades e condições no Android.
- [x] Renderizar banners automáticos para 40%, 50% e demais ofertas com texto e preço corretos no Android nativo.
- [ ] Sincronizar a mesma estrutura de consulta e apresentação no PWA. (Fora do escopo desta etapa; Android nativo é a fonte de verdade.)
- [ ] Validar campanhas simultâneas, precedência visual, expiração e ausência de campos. (A validação local ficou bloqueada porque o SDK exige componentes/licenças que não estão instalados no sandbox; CI será acompanhado após o push.)


## Ajuste visual — detalhe da consulta ACP
- [ ] Remover o título “Dados comerciais retornados pela ACP”, mantendo os valores abaixo.
- [ ] Criar commit e enviar a correção para `main`.
- [ ] Acompanhar o Android CI.

## Publicação autorizada — consulta comercial Android
- [ ] Revisar o conjunto final de arquivos e excluir o PWA e workflows de banners.
- [ ] Criar commit da consulta comercial ACP no Android.
- [ ] Enviar para `main` e acompanhar o CI.
- [ ] Corrigir falhas do CI, se surgirem.

## Investigação de endpoints ACP — regras comerciais
- [ ] Auditar endpoints já permitidos em `AcpApi.kt` e parsers relacionados.
- [ ] Enumerar chamadas somente leitura do fluxo de criação de cartazes e consulta de produtos.
- [ ] Testar endpoints candidatos sem retornar tokens, cookies ou credenciais.
- [ ] Comparar campos de preço, vigência, Clube, cashback, Leve/Pague, atacado e segunda unidade.
- [ ] Classificar endpoints confirmados, candidatos e não autorizados para escrita.
- [ ] Documentar resultados sanitizados e decidir se há evidência suficiente para alterar o parser.

## Pendente
- [ ] Validar em aparelho físico o fluxo completo de edição de produtos após cada release.
- [ ] Revisar mensagens de erro de rede para manter linguagem consistente.
- [ ] Acompanhar o desempenho da paginação do Painel Mestre com catálogo maior.

## Concluído recentemente
- [x] Preview real dos fundos da Home no Painel Mestre.
- [x] Ajuste persistente do enquadramento do fundo: zoom, horizontal e vertical.
- [x] Botão Salvar Prévia publica o enquadramento usado pela Home.
- [x] Tema pessoal funciona também para Mestre/Admin sem interferir na biblioteca global de fundos.

<!-- rebuild-preview-adjustment: 2026-08-30 -->
<!-- updater-retest-release: 2026-08-30T08:32-03:00 -->
<!-- glass-home-fix-dispatch: 2026-08-30 -->
<!-- glass-home-fix-workflow-ready -->
