# Ofertas da loja preferida

Nossas Lojas → Ofertas usa a estrela de loja preferida do NRD. Alterar loja retorna à lista para escolher outra estrela. Sem preferência, não há consulta de ofertas.

Fonte: GET customer.idever.com.br/api/v1/customers/promotions?storeId=<official store ID>, autenticado exclusivamente pela Edge Function nrd-store-offers. As credenciais permanecem no servidor. O catálogo não usa ofertas ACP nem estado de ativação de uma conta pessoal. Ativação continua no aplicativo oficial.

A consulta é específica da unidade, com cache de cinco minutos por ID no servidor e última resposta local separada por loja. A tela consulta novamente a cada minuto enquanto visível e aceita atualização manual. Falhas preservam a última consulta com aviso; primeira falha mostra tentativa novamente. Campanhas vencidas ou futuras são filtradas mesmo em cache, usando America/Fortaleza para datas sem fuso da fonte. A tela reavalia a validade a cada segundo.

Preço zero nas regras da fonte nunca é mostrado como grátis: cashback usa preço base e benefício separado; condições progressivas preservam a regra. Percentual de desconto é truncado como no APK. Imagens ausentes têm substituto neutro. Limite é o máximo da oferta, sem alegar saldo personalizado. Condições completas estão no detalhe.

Validação ao vivo em 23/10/2026: Cidade Jardim (16145121), 137 itens vigentes na consulta; Petra 275 ml R$ 5,79 → R$ 2,31, 60%, máximo 6. Quantidade muda com as campanhas da fonte. A fixture é um registro comercial de teste, não catálogo embarcado para uso real.
