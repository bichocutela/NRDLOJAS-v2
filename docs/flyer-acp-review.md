# Encarte em Consultar Produtos (V2)

1. Painel Mestre > Importar Encarte: PDF, imagem ou link Drive; importer existente.
2. Conferir nome e vigência; revisar cada oferta. Texto OCR e página ficam disponíveis para novas importações. Adicionar oferta manual quando OCR não reconhecer. Revisar ofertas permite corrigir encartes já salvos mantendo o mesmo ID.
3. Buscar na ACP por EAN, código ou descrição e selecionar o produto exato. Conferir valores, quantidades, condições e Clube com o arquivo original; confirmar explicitamente. Grupos precisam de vínculo individual aos produtos participantes, sem inferência por nome/categoria.
4. Salvar e disponibilizar revisadas: mesmo documento config/flyers, permissões existentes. Rascunhos não revisados permanecem editáveis. Nenhuma escrita no ACP. Datas e chave Ativado controlam exibição.
5. Consultar Produtos: balão exibe ofertas revisadas vinculadas exatamente ao código/EAN e vigentes, com fonte encarte e datas. Preço ACP permanece independente. Valores divergentes são informados, promoções simultâneas não são cumuladas automaticamente.

## Regras e compatibilidade

- `reviewed` ausente é falso; encartes antigos precisam de revisão antes da exibição nesta versão.
- `clubCondition` ausente significa não informado. Só o administrador confirma a condição do encarte; isso não prova elegibilidade de CPF na ACP.
- Cashback aparece como retorno posterior; não reduz o preço e não usa diferença De/Por como percentual.
- Sugestão de produto não confirma uma regra OCR. Não usa quantityTake para inferir desconto na segunda unidade.
- Não usa preço atual ACP como base automática para calcular a oferta do encarte. A base deve ser revisada.
- OCR continua sujeito a omissões e confusão de layout. O arquivo original do encarte Semanal-64231.pdf não estava disponível para uma nova comparação visual. A oferta antiga Leve 12/Pague 1 não foi corrigida por suposição.
- Ofertas de Clube não extraídas automaticamente pelo parser existente podem ser incluídas manualmente, com condição explícita.
- Apenas código/EAN é usado na associação; similaridade de nome só auxilia a sugestão inicial.

## Validação

Testes de revisão, ausência/nulos, valores inválidos, vínculo exato, período/desativação, cashback separado de desconto, quantidades com dois dígitos e preservação do texto OCR; suite ACP existente na mesma execução CI.

Ainda exige teste no aparelho: importar arquivo real, selecionar produto, salvar com conta de gerenciamento, abrir o mesmo EAN em outro aparelho e observar Firebase; câmera e sessão ACP reais. Esta mudança não comprova novas regras comerciais fornecidas pela API ACP.
