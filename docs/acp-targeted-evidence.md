# Diagnóstico de cartazes por código ou EAN

O diagnóstico acrescentado ao Consultar Produtos é exclusivo do mesmo acesso Mestre que já vê as ferramentas de exportação. A consulta normal, as regras de preço, os cartazes exibidos no NRD e a autenticação existente não mudam.

## Uso

1. Expandir **Investigar cartazes por código/EAN**.
2. Informar o código interno ou EAN completo. O campo inicia com 2012568001, o Tiroliro solicitado na investigação.
3. Iniciar. A coleta lê os grupos e então percorre o histórico, começando pelas últimas páginas da ordenação crescente. O tamanho de página é 10 para grupos e 25 para histórico. Não é enviado filtro de produto inventado para o endpoint de histórico: a correspondência acontece nos produtos dos registros recebidos.
4. Pausar quando necessário. O checkpoint é gravado a cada página em arquivo próprio do diagnóstico, usando AcpSecureStore, sem alterar o cache de produtos. Continuar retoma da última página inteiramente salva. Cada código/EAN tem seu checkpoint.
5. Salvar resultados da investigação. O JSON contém metadados dos grupos e recortes originais dos produtos correspondentes, com contexto de registro, template e posição. Compactar em ZIP para anexar caso o aplicativo de chat rejeite JSON.

## Contrato e limites

- Apenas GET ProductGroup/all e TemplatePrintLog/all, usando a sessão e a renovação já implementadas. Não há POST/PUT de produtos, campanhas ou logs.
- Grupo: usa items[].products[] observado no código do frontend. A resposta real de grupos ainda precisa ser capturada. Nome de grupo não define correspondência nem percentual.
- Histórico: JSON.parse(items[].dataLog).composedTemplates[].items[], comprovado pelo teste.zip enviado pelo usuário. Aceita também dataLog já desserializado; isso é tolerância do parser, não evidência de um novo formato do servidor.
- Correspondência exata por code OU barCode, sem substring e sem inferência por descrição, preço ou nome do template.
- Mantém os campos originais do produto correspondente, inclusive strings numéricas, campos ausentes, nulos, secondUnitDiscount, cashback e validOffer, quando vierem. Não transforma os dados em ofertas atuais nem faz cálculos comerciais.
- `response` no arquivo exportado é o relatório da coleta; `format` identifica que são recortes, não a resposta HTTP inteira. Cada ocorrência preserva `product`, `template`, `record`/`group` e `productPath` para rastreio. Produtos alheios ao alvo não são exportados.
- Registros ilegíveis são contabilizados; acesso indisponível a grupos e alterações do total do histórico ficam explícitos. "Varredura finalizada" descreve o término da travessia, não vigência comercial nem garantia de completude de dados que mudaram.
- Falhas de paginação/repetição interrompem sem marcar conclusão. Cancelamento preserva somente páginas completas. A chamada em andamento usa o timeout normal do cliente antes de devolver o cancelamento.
- Limites: 3.000 páginas e 8 MB de checkpoint. Ao atingir um limite, salvar o resultado parcial. Não há coleta em background após sair da tela, sincronização automática de ofertas ou efeito sobre os dados ACP.

## Evidência e testes

`app/src/test/resources/acp/history-page-0-observed.json` provém de teste.zip/acp-historico-pagina-0.json, fornecido pelo usuário em 14/09/2026. Foram removidos apenas user/userId; corpo do dataLog preservado. O registro 6306 tem quatro ocorrências impressas do Trident (code 2010473004, barCode 7622210564337), value="2.99", clubValue="1.99", validOffer="03/02/2026", showOffersExpirationDate=true, dueDate=null, cashback=null e cashbackValue=0. Não contém secondUnitDiscount.

Testes cobrem esses campos reais e a ausência do Tiroliro nesse recorte. Paginação simulada, pausa/retomada e grupos sintéticos testam exclusivamente o transporte e a coleta; não são apresentados como prova comercial.
