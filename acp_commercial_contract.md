# Contrato comercial unificado para a aba Consultar Produtos

## Objetivo

A aba **Consultar Produtos** deve apresentar a fotografia comercial do produto no momento da consulta. O contrato não pode escolher apenas uma oferta e descartar as demais: um mesmo produto pode ter preço normal, preço Clube, cashback e uma condição de segunda unidade ao mesmo tempo. Cada oferta precisa conservar sua origem, validade e condições de elegibilidade para que o banner não prometa uma vantagem que não esteja confirmada pelo ACP.

## Modelo conceitual

```text
AcpCommercialProduct
├── identidade: id ACP, código interno, EAN, descrição, unidade, categoria
├── preços: normal, anterior, vigente, clube, atacado
├── disponibilidade: estoque, quantidade por CPF, quantidade mínima de atacado
├── vigência: início, fim, estado calculado
├── ofertas[]
│   ├── família: PRICE | DE_POR | CLUB | SECOND_UNIT | TAKE_PAY | CASHBACK | WHOLESALE
│   ├── título e headline
│   ├── preço de referência e preço resultante
│   ├── percentual ou valor de retorno
│   ├── quantidade leve/pague ou segunda unidade
│   ├── elegibilidade e restrições
│   ├── início/fim da oferta
│   ├── origem: Product, Campaign, Template ou outra chamada observada
│   └── confiança: CONFIRMED | PARTIAL | TEXT_ONLY
└── diagnóstico: endpoints consultados, horário, sessão, avisos e campos ausentes
```

## Famílias que devem ser preservadas

| Família | Dados mínimos | Exibição esperada |
|---|---|---|
| Preço normal | valor vigente, unidade | Preço principal do produto |
| De/Por | preço anterior, preço promocional, vigência | Banner com “DE” riscado e “POR” destacado |
| Clube | preço Clube, preço de referência, condição de adesão | Banner azul “PREÇO CLUBE” |
| Segunda unidade | percentual ou valor, unidade afetada, limite e vigência | Banner “40%/50% NA SEGUNDA UNIDADE” |
| Leve/Pague | quantidade levada, quantidade paga, preço base e média calculada | Banner “LEVE X, PAGUE Y” |
| Cashback | percentual ou valor, condição de crédito e validade | Banner “CASHBACK”, sem tratar como desconto imediato |
| Atacado | preço, quantidade mínima, unidade ou grupo elegível | Banner com preço por unidade e mínimo |
| Limite | limite por CPF, compra ou campanha | Texto de restrição visível junto da oferta |

## Regras de normalização

Os valores devem ser convertidos para uma representação decimal exata antes da apresentação. Percentuais devem aceitar números e strings com vírgula ou símbolo `%`, mas somente podem ser exibidos como desconto quando a origem indicar explicitamente a família correspondente. Datas ISO devem ser convertidas para o formato local, mantendo hora quando ela existir. Campos nulos ou ausentes devem resultar em “não informado”, nunca em zero inventado.

A consulta deve manter uma lista de ofertas, mesmo quando duas ofertas pertencem à mesma família. A deduplicação só pode ocorrer quando título, valor, referência, condição e vigência forem iguais. Uma oferta de Clube não deve substituir uma oferta de segunda unidade; uma oferta de cashback não deve ser confundida com preço promocional.

## Estado de confiança

`CONFIRMED` significa que o ACP retornou o campo estruturado. `PARTIAL` significa que parte da regra foi retornada, mas falta vigência, limite ou condição. `TEXT_ONLY` significa que a informação só apareceu em texto de campanha ou descrição. O banner deve mostrar a oferta, mas também deve mostrar a restrição correspondente quando a confiança não for `CONFIRMED`.

## Precedência visual, sem perda de dados

A precedência serve somente para definir a ordem dos banners, não para eliminar ofertas. A ordem recomendada é: segunda unidade, Leve/Pague, Clube, De/Por, atacado, cashback e preço normal. Se houver várias ofertas de primeira classe, todas devem aparecer em uma lista horizontal ou vertical rolável. O operador deve conseguir abrir o detalhe e visualizar todas as condições.

## Campos de origem ACP

O normalizador deve aceitar os campos já mapeados no Android, incluindo `value`, `previousValue`, `clubValue`, `wholesaleValue`, `wholesaleQuantity`, `quantityTake`, `quantityPay`, `cashback`, `cashbackValue`, `secondUnitDiscount` e `unitLimitPerCPF`. Em campanhas, também deve procurar os mesmos conceitos dentro de objetos `product`, `promotion`, `offer`, `condition`, `rule` e vínculos de produto-campanha, sem assumir que qualquer nome novo seja confiável antes de registrar sua origem.

## Comportamento quando o ACP não fornece uma regra

Se `Campaign/all` estiver vazio ou se `Product/all` não tiver a condição, a consulta deve informar que aquela oferta não foi confirmada para o produto consultado. O aplicativo não deve fabricar um banner de 40% ou 50% com base em um template, em um nome de campanha ou em uma pesquisa externa. Templates definem apresentação; somente dados comerciais retornados pelo ACP podem preencher preço e desconto.

## Paridade Android/PWA

Android e PWA devem consumir o mesmo vocabulário de famílias e o mesmo formato de oferta. O Android pode usar Compose para renderizar os cartões paisagem e verticais; o PWA pode usar componentes equivalentes, mas não deve recalcular regras de negócio de modo diferente. A validação deve comparar, para o mesmo produto e instante, a quantidade de ofertas, famílias, valores, datas e restrições.

## Critérios de aceite

A implementação será considerada alinhada quando a consulta mostrar, sem edição manual, todas as ofertas estruturadas disponíveis no ACP; quando os banners de 40% e 50% exibirem o percentual retornado e a segunda unidade; quando Clube, cashback, Leve/Pague, atacado e De/Por aparecerem simultaneamente quando existirem; quando a vigência e os limites forem visíveis; e quando dados ausentes forem tratados como ausentes, sem transformar ausência em desconto.
