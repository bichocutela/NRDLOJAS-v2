# NordestaoCatalogResolver

Resolver de imagens e links do catálogo público do Nordestão para promoções vindas do ACP.

## Contrato de entrada

O resolver recebe linhas ACP com pelo menos:

```json
{
  "codproduto": "00123",
  "desc_prod": "Chocolate Nestlé Recheados Prestígio 90g",
  "imagem": null,
  "linkloja": null
}
```

O snapshot do catálogo precisa ser normalizado para:

```json
{
  "produto_id": "54487",
  "codigo_interno": "00123",
  "descricao": "Chocolate Nestlé Recheados Prestígio 90g",
  "slug": "chocolate-nestle-recheados-prestigio-90g",
  "imagens": [{"src": "https://cdn.../54487.jpg"}]
}
```

`codigo_interno` é opcional. Quando existir, ele tem prioridade e o match é exato. Sem código, o resolver normaliza acentos/pontuação e calcula uma pontuação de descrição. Resultados ambíguos são rejeitados.

## Ordem de resolução

1. `codproduto` versus `codigo_interno`, preservando zeros à esquerda;
2. descrição normalizada;
3. compatibilidade de números e unidades, como `90g` e `1L`;
4. margem mínima entre o primeiro e o segundo candidato;
5. somente depois de aceito, preencher `imagem` e `linkloja` ausentes.

O resolver não baixa nem redistribui imagens. O `loadCatalog` deve usar um feed/API autorizado ou um snapshot obtido com permissão.

## Teste local

```bash
node --test supabase/functions/nordestao-catalog-resolver/handler.test.mjs
```

## Teste com amostra real

Crie um script local que carregue dois arquivos JSON:

- `acp-sample.json`: linhas reais de `/promocoes` contendo `codproduto` e `desc_prod`;
- `nordestao-catalog.json`: snapshot autorizado do catálogo com `produto_id`, `codigo_interno`, `descricao`, `slug` e imagens.

Exemplo de execução:

```js
import {readFile} from 'node:fs/promises';
import {resolvePromotions} from './handler.mjs';

const acp = JSON.parse(await readFile('./acp-sample.json', 'utf8'));
const catalog = JSON.parse(await readFile('./nordestao-catalog.json', 'utf8'));
const out = resolvePromotions(acp, catalog, {minScore: 0.86});
await Bun.write('./resolver-result.json', JSON.stringify(out, null, 2));
```

Com Node puro, use `writeFile` em vez de `Bun.write`.

O relatório deve ser revisado antes de ativar a sincronização:

- matches `internal_code`: podem ser promovidos automaticamente;
- matches `description`: devem ser revisados na primeira amostra;
- matches `ambiguous`/`none`: não devem receber imagem automaticamente;
- conferir especialmente marca, sabor, volume/peso e embalagem.

## Integração opcional no `nrd-price-gateway`

O gateway já aceita um `catalogLoader` opcional. Na Edge Function, ele é ativado somente quando esta variável existe:

```text
NORDESTAO_CATALOG_SNAPSHOT_URL=https://host-autorizado/catalog/nordestao.json
```

O endpoint deve responder com um array de produtos ou com `{ "items": [...] }`. A função exige HTTPS, limita o corpo a 8 MB e mantém o snapshot em memória por 15 minutos. O valor da URL deve apontar para um feed/snapshot autorizado; o gateway não faz scraping do bundle do site.

Opcionalmente:

```text
NORDESTAO_CATALOG_MIN_SCORE=0.86
```

O enriquecimento é tolerante a falhas: se o snapshot estiver indisponível, a resposta ACP original continua sendo entregue. O gateway acrescenta apenas metadados de catálogo aos itens, como `imageUrl`, `productUrl`, `catalog_match_type`, `catalog_match_score` e `catalog_product_id`; credenciais, cookies e tokens ACP continuam fora da resposta.

## Atualização diária automática

O workflow `.github/workflows/update-nordestao-catalog.yml` executa todos os dias às **03:00 no horário de Brasília** (`06:00 UTC`) e também pode ser iniciado manualmente. Ele:

1. baixa o feed HTTPS autorizado;
2. limita o download a 8 MB;
3. normaliza e valida IDs, códigos e descrições;
4. rejeita duplicatas e snapshots menores que o mínimo configurado;
5. publica somente o arquivo validado em `catalog/nordestao.json`;
6. lê novamente a URL pública e verifica a estrutura publicada.

Configure no GitHub, em **Settings → Secrets and variables → Actions**:

| Tipo | Nome | Valor |
|---|---|---|
| Secret | `NORDESTAO_CATALOG_SOURCE_URL` | URL HTTPS do feed autorizado do Nordestão |
| Secret | `SUPABASE_URL` | URL do projeto Supabase |
| Secret | `SUPABASE_SERVICE_ROLE_KEY` | Service-role key, somente no GitHub Actions |
| Variable | `NORDESTAO_CATALOG_MIN_ITEMS` | Quantidade mínima esperada, por exemplo `100` |

Depois de publicar o workflow, configure uma única vez na Edge Function:

```text
NORDESTAO_CATALOG_SNAPSHOT_URL=https://SEU_PROJETO.supabase.co/storage/v1/object/public/nrdlojas-images/catalog/nordestao.json
```

Não coloque `SUPABASE_SERVICE_ROLE_KEY` no APK, no snapshot ou em variáveis públicas. Se o feed vier vazio, inválido ou abaixo do mínimo, o workflow falha antes do upload e o snapshot anterior permanece intacto.

Antes de ativar em produção:

1. obtenha uma amostra pareada real e meça a precisão;
2. confirme que o feed do Nordestão permite esse uso;
3. configure os secrets do workflow;
4. execute manualmente o workflow uma vez;
5. configure a URL estável na Edge Function;
6. execute os testes do gateway;
7. valide que produtos ambíguos permanecem sem imagem.
