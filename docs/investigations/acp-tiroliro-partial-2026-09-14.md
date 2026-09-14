# Tiroliro: diagnóstico parcial recebido em 14/09/2026

Fonte: `investigação.zip/acp-investigacao-produto.json`, enviado pelo usuário. O JSON original foi preservado em `acp-tiroliro-partial-2026-09-14.json` (2.889 bytes; SHA-256 `0e465a6e6facef870e0389c4d0df46871262bb6ba85c73e6dbe69b26299d6764`). O ZIP está íntegro e o JSON não está vazio.

## Resultado observado

- Alvo: código interno `2012568001` (Tiroliro).
- Estado salvo: `phase=history`, `pages=3`, `records=12`, `matches=[]`, `unparsed=0`, `groupsAvailable=true`.
- Foram registrados 12 grupos de duas páginas e 2.068 entradas de produtos somadas pelos contadores. Essas entradas não representam necessariamente produtos distintos. O coletor não reteve correspondência exata com o alvo nesses grupos; o relatório não contém os produtos completos de cada grupo.
- Grupos com descrições promocionais: id 3, `Higiene Oral 50% NA SEGUNDA UNIDADE` (168 entradas); id 4, `Desodorante 50% NA SEGUNDA UNIDADE` (186); id 11, `Elma Chips LEVE3 PAGUE2` (96); id 49, `encarte 50% 30.07 a 02.08` (109); id 50, `capilar 60% segunda unidade` (64).
- A sondagem do histórico informou 15.849 registros e 634 páginas. O checkpoint aponta a página 632 como próxima, após a página 633. `historySignature` está vazio e todos os 12 registros contados são grupos: nenhum cartaz foi contabilizado no resultado salvo.
- Entre `startedAtMillis` e `savedAtMillis` decorreram 12,319 segundos, de 13:04:58,680 a 13:05:10,999 UTC. Isso mede o intervalo até a última página salva, não necessariamente toda a duração da tentativa.

## Interpretação e limites

Conforme o código do coletor, o estado é compatível com uma última página do histórico recebida vazia. O relatório não inclui a resposta HTTP dessa página nem a razão da interrupção. Não permite distinguir pausa manual, erro posterior ou mudança no histórico. Não atribuir a causa à sessão ou ao servidor sem nova evidência.

| Pendência | O que este arquivo comprova |
| --- | --- |
| Vínculo do Tiroliro | Nenhuma correspondência retida nos 12 grupos; histórico incompleto. Não demonstra ausência no histórico inteiro. |
| Percentual da segunda unidade | Existem descrições de grupos com 50% e 60%. Não há produto/cartaz vinculado ao Tiroliro nem `secondUnitDiscount` que comprove sua regra. |
| Cashback | Nenhum produto correspondente foi exportado; não há campo de cashback para analisar. Ausência no relatório não significa cashback zero no sistema. |
| Vigência comercial | Não vieram `validOffer`, `showOffersExpirationDate` ou período de campanha de um produto correspondente. Datas em nomes de grupos não comprovam vigência, ano ou aplicação ao Tiroliro. |

## Continuidade

O coletor existente pode retomar a partir da página 632 com **Continuar investigação**, usando o mesmo código e aparelho, preservando as páginas salvas. Conferir o término explícito da varredura antes de exportar. Se houver nova interrupção, a mensagem exibida na tela é necessária para diagnosticar a causa que este arquivo não registra. A coleta de cashback de outro produto exige seu próprio código/EAN.

Nenhuma regra comercial, preço, sessão ou funcionalidade do aplicativo foi alterada a partir desta evidência parcial.
