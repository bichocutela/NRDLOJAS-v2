# Firestore: auditoria do fluxo de promoções

## Evidência no código

- O antigo ServerCache armazenava classificações por produto, resumos, snapshots e leases no Firestore. BatchGet agrupa transporte, mas cada documento consultado continua sendo uma leitura. Várias instâncias frias repetem essas consultas; o cache em memória não é global.
- PromotionSyncCoordinator consulta status a cada 60 segundos com o app em primeiro plano. Os workers de promoções e catálogo executam a cada 15 minutos. O coordenador coalesce chamadas próximas, mas o worker de catálogo também consulta o gateway.
- PromotionConfigCache consulta dois documentos de configuração com TTL de cinco minutos. Reiniciar o processo elimina esse cache em memória.
- O autorizador do gateway consulta configuração, sessão e concessão conforme a identidade. Esses documentos continuam no Firebase para manter revogação imediata. Existem também listeners de configuração, banners e acesso no app.

## Correção aplicada

Snapshots, classificações, leases e associações oficiais de código/EAN passam para nrd_promotion_cache no Supabase. A tabela tem RLS e acesso apenas de service_role; clientes não recebem essa credencial. Escritas por versão impedem que um worker atrasado sobrescreva outro. A consulta oficial usa cache persistente e progride além do limite por resposta. Falhas temporárias preservam imagem e categoria confirmadas, mantendo preços ACP atualizados.

Cada consulta de permissões registra GATEWAY_FIRESTORE_READ com coleção e status HTTP, sem ID pessoal ou token. Isso permite contar tentativas e separar respostas 200, 404 e erros nos logs do gateway. Não representa sozinho o total faturado do projeto.

## Medição ainda necessária

Não há acesso às métricas do Firebase/Google Cloud nesta sessão. Conferir no projeto appcodigo-7f245: document reads/writes/deletes, listeners e conexões, por intervalo e antes/depois da implantação. Cruzar com logs do gateway e número de dispositivos ativos. Erros 429 comprovam esgotamento, mas não quantificam leituras nem atribuem todo o consumo a um componente.

Fontes oficiais: https://firebase.google.com/docs/firestore/pricing e https://firebase.google.com/docs/firestore/monitor-usage.
