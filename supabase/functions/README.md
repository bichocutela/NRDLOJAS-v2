# Edge Functions

As funções de produção devem permanecer versionadas nesta pasta.

## Ativas e utilizadas

- `send-fcm`: envio de notificações do aplicativo.
- `upload-image`: upload de imagens administradas pelo Mestre.

## Retiradas

- `device-installations`: código removido do Android e do repositório, coleta
  encerrada e registros históricos excluídos em 29/08/2026. O deployment
  residual responde somente HTTP 410 e não acessa dados.
- `add-padaria-products-once`: carga temporária encerrada; responde HTTP 410.
- `notify-padaria-product-once`: disparo temporário encerrado; responde HTTP
  410.
- `notify-legacy-update-once`: disparo para versões antigas encerrado;
  responde HTTP 410.

Funções temporárias de carga ou disparo único não devem permanecer publicadas
depois de cumprirem sua finalidade.
