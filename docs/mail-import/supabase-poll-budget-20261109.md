# Redução de consumo do conector de relatórios NRD (09/11/2026)

## Problema medido

- O script Gmail consulta a função **nrd-mail-import** aproximadamente uma vez por minuto para aceitar o comando do Mestre na próxima execução.
- Em 24 h, os logs mostraram cerca de 2.391 GETs e 1.180 PATCHs na tabela `nrd_mail_settings`. Esse fluxo inclui também consultas de outras operações, portanto não se deve atribuir todos os números ao poll sem medição por operação.
- O poll antigo, após validar a chave de ingestão, gravava `scan_seen_at` e fazia **uma segunda leitura** das colunas de scan, mesmo quando o estado não mudava.

## Mudança do servidor

- A leitura que autentica a chave também busca os campos de status da execução. O poll usa essa mesma linha validada; economiza um GET por poll.
- O coração de vida `scan_seen_at` só é atualizado caso esteja vazio ou tenha pelo menos 10 minutos. O filtro no PATCH é condicional para reduzir gravações simultâneas.
- O script continua disparando uma vez por minuto. **Não alteramos**, nem é necessário reinstalar, o Apps Script. O botão Buscar documentos recebidos permanece respondendo na próxima verificação do conector; a entrega e o ACK não mudam.
- Chaves não são armazenadas no aparelho; cada poll continua passando pela verificação online de `key_hash` no banco. O cliente recebe apenas os IDs de estado autorizados.

## Economia estimada e observação

A rotina de poll em si pode reduzir de aproximadamente **2 acessos + 1 gravação** para **1 acesso e, em média, 1 gravação a cada 10 minutos**. Isso **não é uma redução medida de egress do projeto**: respostas desses endpoints são pequenas e o principal custo de egress pode vir de imagens e payloads do gateway.

Comparar o volume diário de GET/PATCH em `/rest/v1/nrd_mail_settings`, executar um scan manual e observar se `scan_finished_id` acompanha `scan_request_id`. Não remover cron nem documentos existentes.
