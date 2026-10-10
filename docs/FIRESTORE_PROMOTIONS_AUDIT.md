# Firestore: auditoria do fluxo de promoções

## Evidência no código

- O antigo ServerCache armazenava classificações por produto, resumos, snapshots e leases no Firestore. BatchGet agrupa transporte, mas cada documento consultado continua sendo uma leitura. Várias instâncias frias repetem essas consultas; o cache em memória não é global.
- PromotionSyncCoordinator consulta status a cada 60 segundos com o app em primeiro plano. Os workers de promoções e catálogo executam a cada 15 minutos. O coordenador coalesce chamadas próximas, mas o worker de catálogo também consulta o gateway.
- PromotionConfigCache consulta dois documentos de configuração com TTL de cinco minutos. Reiniciar o processo elimina esse cache em memória.
- O autorizador do gateway consulta configuração, sessão e concessão conforme a identidade. Esses documentos continuam no Firebase para manter revogação imediata. Existem também listeners de configuração, banners e acesso no app.

## Catálogo global: amplificação das leituras

FirebaseService.createProductUsageFlow mantém um listener sem limite na coleção products. MainViewModel coleta esse fluxo no viewModelScope, sem vínculo ao estado STARTED da interface. O fluxo compartilhado evita duplicação dentro do mesmo processo, mas cada aparelho ainda tem seu próprio listener.

MainViewModel.onProductSearched chama registerGlobalProductView após a proteção local contra toques repetidos. Essa função grava searchCount e lastViewedAt no documento products. Cada alteração de produto gera leitura do documento alterado em cada listener ativo; não significa reler toda a coleção em cada alteração. Por exemplo, uma alteração recebida por 100 listeners pode gerar 100 leituras. Esse exemplo explica a multiplicação, não mede a quantidade de aparelhos conectados.

A consulta inicial do listener lê os documentos do catálogo; reconexões também podem cobrar uma nova consulta conforme a persistência e a duração da desconexão. Manutenção, criação/restauração de snapshots e carregamento manual incluem leituras completas da coleção. Separar estatísticas de uso dos documentos do catálogo e medir listeners é a próxima prioridade; a migração do cache de promoções não corrige esse fluxo automaticamente.

## Correção aplicada

Snapshots, classificações, leases e associações oficiais de código/EAN passam para nrd_promotion_cache no Supabase. A tabela tem RLS e acesso apenas de service_role; clientes não recebem essa credencial. Escritas por versão impedem que um worker atrasado sobrescreva outro. A consulta oficial usa cache persistente e progride além do limite por resposta. Falhas temporárias preservam imagem e categoria confirmadas, mantendo preços ACP atualizados.

Cada consulta de permissões registra GATEWAY_FIRESTORE_READ com coleção e status HTTP, sem ID pessoal ou token. Isso permite contar tentativas e separar respostas 200, 404 e erros nos logs do gateway. Não representa sozinho o total faturado do projeto.

## Medição ainda necessária

A auditoria somente de leitura rodou pelo GitHub Actions com a conta de serviço existente. A API Cloud Monitoring retornou HTTP 403 PERMISSION_DENIED para leituras, gravações, exclusões, conexões e listeners. Portanto não há números reais disponíveis nesta sessão. A conta precisa da permissão monitoring.timeSeries.list; o papel roles/monitoring.viewer fornece acesso somente de leitura. Não foram alteradas permissões IAM. Conferir no projeto appcodigo-7f245: document reads/writes/deletes, listeners e conexões, por intervalo e antes/depois da implantação. Cruzar com logs do gateway e número de dispositivos ativos. Erros 429 comprovam esgotamento, mas não quantificam leituras nem atribuem todo o consumo a um componente.

Fontes oficiais: https://firebase.google.com/docs/firestore/pricing e https://firebase.google.com/docs/firestore/monitor-usage.

## Validação da migração

Gateway v23 ativo; 53 testes Node passaram. A validação CI real confirmou a imagem e o departamento de 2021000 e a sincronização diferencial de 1.912 produtos no Supabase. As permissões públicas continuam bloqueadas pelo erro explícito de cota do Firestore; esse bloqueio não foi contornado.
