# Redução de consumo Supabase — quatro blocos — 09/11/2026

Projeto: `kkayksyzksexoarpfxyj`, repositório `bichocutela/NRDLOJAS-v2`.

## 1. Imagens antigas e Drive

O manifesto de aparência atual já prioriza links do Drive. O Storage do Supabase, entretanto, ainda atende downloads de versões antigas e fallbacks (fundos, cartões e Consulta de Preços).

A nova versão Android usa um `ImageLoader` único para imagens via Coil e um cache em disco limitado a 96 MiB. O interceptor concede reutilização local por sete dias **somente** a imagens públicas de caminhos conhecidos com identificador UUID no nome. Arquivos dinâmicos, JSON de aparência, PDF, MP4, URLs assinadas/privadas e links do Drive não recebem TTL forçado. O cache pode ser limpo pelo Android sob pressão de armazenamento.

Não apaga imagens, não força substituição de arquivos sem correspondência confirmada e não altera o manifesto/Drive. Para atingir aparelhos com APK antigo, é necessário instalar o APK atualizado.

## 2. Consultar Preços

O warmup suplementar do catálogo ACP buscava até seis páginas a cada 10 minutos no primeiro plano e tinha outro WorkManager periódico de 15 minutos.

A nova versão compartilha um mutex e uma marca de horário persistida para limitar o warmup **automático** bem-sucedido a uma vez por hora, além de atualizar o WorkManager para um intervalo de 60 minutos. A consulta por código, nome e barras, `getFresh` e o refresh solicitado pelo usuário **não** são limitados por essa regra.

## 3. Promoções

O monitor de primeiro plano fazia `requestSync()` a cada 60 segundos, ainda que o cache de configuração tivesse cinco minutos. O novo intervalo de primeiro plano é de cinco minutos, com reaproveitamento de resultado recente. Os botões interativos ainda fazem sincronização imediata; o worker periódico em segundo plano permanece em 15 minutos.

A janela de confirmação do snapshot ACP usada nos avisos passa para seis minutos, acompanhando o novo intervalo e evitando falsos avisos suprimidos. O servidor continua sincronizando a fonte ACP a cada cinco minutos. Os produtos vencidos ainda são filtrados pelas datas; remoções no servidor permanecem refletidas no próximo sincronismo.

Tradeoff explícito: com o app aberto, uma oferta recém-publicada pode demorar até o próximo ciclo (~5 min), exceto quando atualizada manualmente. Não afirmar instantaneidade.

## 4. Controle e alerta

Monitoramento diário `Vigiar cota NRD Supabase` criado em ChatGPT Automations, com verificação das funções, erros de cota, gravações do cache, consultas repetidas, documentos e downloads de imagens. Quando houver acesso às métricas oficiais, alertar em 60/80/95% dos limites atuais. Logs de request não equivalem à métrica faturada de egress. Não publicar estimativas como cobrança.

## Base técnica medida antes das novas alterações

- Nas horas anteriores às otimizações de 09/11, `rpc/nrd_cache_write` mostrou picos históricos superiores a 800 chamadas/h; após a correção do enriquecimento do gateway caiu significativamente em certas horas, sem controle de dispositivos concorrentes. É amostra indicativa, não economia faturada.
- Downloads históricos do Storage ainda ocorrem; na janela consultada, fundos de temas e cartões foram os grupos com maior transferência mensurável.
- Gateway no Supabase já implantado em versão 67, conector Gmail em versão 5, com rotinas comerciais de cinco minutos. Essas mudanças anteriores permanecem intactas.
- Nenhuma tabela, credencial, bucket, permissão ou registro foi excluído nesta etapa.

## Verificação da entrega

Requer CI Android verde no commit exato, testes `NrdPublicImageCacheTest`, `AcpFeaturedBudgetTest`, `PromotionsUiStateTest`, build assinado e observação dos logs após instalação. A nova versão Android deve ser instalada para que os blocos 1–3 tenham efeito nos dispositivos; o bloco 4 é independente.
