# Ofertas da loja preferida

Nossas Lojas → Ofertas usa a estrela de loja preferida do NRD. Alterar loja retorna à lista para escolher outra estrela. Sem preferência, não há consulta de ofertas.

Fonte: GET customer.idever.com.br/api/v1/customers/promotions?storeId=<official store ID>, autenticado exclusivamente pela Edge Function nrd-store-offers. As credenciais permanecem no servidor. O catálogo não usa ofertas ACP nem estado de ativação de uma conta pessoal. Ativação continua no aplicativo oficial.

A consulta é específica da unidade, com cache de cinco minutos por ID no servidor e última resposta local separada por loja. A tela consulta novamente a cada minuto enquanto visível e aceita atualização manual. Falhas preservam a última consulta com aviso; primeira falha mostra tentativa novamente. Campanhas vencidas ou futuras são filtradas mesmo em cache, usando America/Fortaleza para datas sem fuso da fonte. A tela reavalia a validade a cada segundo.

Preço zero nas regras da fonte nunca é mostrado como grátis: cashback usa preço base e benefício separado; condições progressivas preservam a regra. Percentual de desconto é truncado como no APK. Imagens ausentes têm substituto neutro. Limite é o máximo da oferta, sem alegar saldo personalizado. Condições completas estão no detalhe.

Validação ao vivo em 23/10/2026: Cidade Jardim (16145121), 137 itens vigentes na consulta; Petra 275 ml R$ 5,79 → R$ 2,31, 60%, máximo 6. Quantidade muda com as campanhas da fonte. A fixture é um registro comercial de teste, não catálogo embarcado para uso real.


## Sininho de notificações (Android)

O botão ao lado de **Ordenar**, na aba Nossas Lojas → Ofertas, permite escolher **uma** categoria de aviso por aparelho:

- Acima de 50% (padrão na primeira instalação; estritamente maior que 50)
- Acima de 60% (estritamente maior que 60)
- Abaixo de 50% (1 a 49)
- Abaixo de 60% (1 a 59)
- Produtos com cashback (somente cashback explícito da fonte)
- Desativadas

O sininho não altera os cards, preços, promoções ou a conta pessoal do Nordestão. A seleção é local. As notificações gerais do NRD e a permissão do Android continuam obrigatórias; o padrão selecionado não concede permissões do sistema sozinho.

A verificação aproveita a mesma fonte/cache de ofertas da loja preferida: enquanto a aba está aberta, utiliza a resposta já carregada; em segundo plano, WorkManager pode consultar no mínimo a cada 60 minutos com internet. A periodicidade é aproximada e sujeita ao gerenciamento de bateria do Android, portanto os avisos não são garantidos em tempo real. Nenhuma tabela, cron do Supabase ou login do cliente foi criado.

A primeira resposta nova da loja/filtro cria uma linha de base sem notificar centenas de ofertas antigas. Respostas antigas (stale) não alteram a linha de base. Novas ofertas ou mudanças de preço que satisfaçam o filtro geram um aviso agrupado, deduplicado por loja/filtro e assinatura comercial. O toque na notificação abre a aba Ofertas da loja preferida.
