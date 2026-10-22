# Cashback Aurora — divergência em investigação

Produto no NRD: Linguiça Fininha Aurora Premium 600g, código 2039104,
EAN 7891164004774. Captura enviada em 12/09/2026 mostra preço principal 16,99,
referência 36,99 e categorias Varejo / De-Por, sem cartão cashback.
Outra captura, de um aplicativo de ofertas, mostra descrição compatível,
preço 36,99 e 30% de cashback. O EAN não está visível nessa segunda captura.
Ela não é um payload da ACP nem comprova combinação de cashback com o preço 16,99.

## Investigação

O código do NRD já lê cashback e cashbackValue de Product/all e de campanhas.
Sem resposta real dessa busca não é possível concluir se houve campo ausente,
valor nulo, modalidade em outra origem ou falha de leitura. Não foi usado o preço
anterior para deduzir percentual. Não foram fixados 30%, limite ou validade no app.

O portal autenticado listou o template 481 — Cashback %, tipo Varejo, A3.
Depois de selecionar o template, a navegação foi redirecionada para a tela de
login antes de consultar Aurora. O motivo não foi observado; nenhum cabeçalho,
cookie ou credencial foi lido. Isso é diferente da aba vazia observada anteriormente.
Nenhum dado foi alterado no ACP. A investigação de grupos continua pendente.

## Correção de apresentação

Os cartões de cashback de campanhas não tinham headline; agora destacam o
percentual ou valor de retorno, assim como os cartões de Product/all, e explicam
que não é desconto imediato. O preço de pagamento não é reduzido pelo cashback.
Não foram alterados o parser, as regras de vínculo nem a elegibilidade.

Quando a consulta termina sem cashback, a ficha explicita que a ACP não informou
o dado. Se a consulta complementar falha, informa que ficou incompleta, evitando
apresentar falha de consulta como ausência de cashback. Isso não confirma que o
produto não tenha cashback no aplicativo de ofertas.

O teste novo usa fixture sintética e valida apenas apresentação de campos já
suportados. Não é validação comercial real da promoção Aurora. O próximo dado
necessário é o diagnóstico da busca exata e da ficha desse EAN, com Product/all,
Campaign/all e Product/integrationInfo, sem credenciais ou cabeçalhos.
