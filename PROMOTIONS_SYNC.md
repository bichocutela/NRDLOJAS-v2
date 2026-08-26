# Promoções — integração com Nossa Gente

A tela **Promoções** usa o mesmo fluxo de autenticação do Nossa Gente. O funcionário informa CPF e senha, o NRD envia `POST /auth/login` e guarda somente o token de sessão cifrado com Android Keystore. A senha não é persistida.

## Rotas usadas

```text
POST /auth/login       payload: { "cpf": "<CPF>", "senha": "<senha>" }
GET  /promocoes?limit=10
```

O cliente envia a consulta com:

```http
Authorization: Bearer <token-da-sessao>
Accept: application/json
```

A API atual retorna uma lista plana, com uma linha por produto e loja, usando `loja`, `codproduto`, `desc_prod`, `categoria`, `datainicio`, `datafim`, `preco_normal`, `preco_promo`, `imagem` e `linkloja`. O NRD agrupa as linhas por produto e intervalo de validade e exibe cada loja como uma oferta individual, preservando código, preços, imagem e link. O parser também mantém compatibilidade com respostas aninhadas usando `data`, `promocoes`, `promotions`, `items`, `produtos`, `products`, `itens` e `ofertas`.

## Atualização

Enquanto a tela está aberta, o NRD faz uma verificação silenciosa a cada 60 segundos. A resposta é normalizada e recebe uma assinatura SHA-256 estável, considerando produto, loja, período, preços, desconto, imagem e link. Se a assinatura não mudou, a lista visível permanece intacta e o botão **Atualizar** fica neutro.

Quando a assinatura muda, o NRD mantém os produtos atuais na tela, guarda a nova resposta em memória e destaca o botão **Atualizar**. O funcionário pode tocar no botão para aplicar a nova lista; depois disso, o botão volta ao estado neutro. Se o funcionário tocar no botão estando neutro, o app apenas verifica a API e só destaca o botão caso encontre uma mudança.

O carregamento inicial continua exibindo progresso. A consulta automática não substitui a lista durante a navegação, e a preparação dos grupos ocorre fora da thread principal para evitar travamentos. Um retorno HTTP 401/403 apaga a sessão local e solicita novo login.

Essa atualização é um fallback controlado. Para sincronização realmente instantânea quando uma promoção ou produto em oferta mudar, o backend do Nossa Gente precisa publicar um evento/webhook ou alimentar um canal compartilhado de eventos. O APK não expõe webhook e o endpoint `/promocoes` exige token; por isso não é seguro colocar uma credencial administrativa no NRD nem depender do token de outro funcionário.

## Configuração

A URL pode ser alterada por ambiente com:

```text
NOSSA_GENTE_API_BASE_URL=https://app.nordestao.com.br/nossa-gente/v1
```

Para homologação, use uma URL de homologação e um `applicationId`/flavor separado. Nunca coloque CPF, senha ou token em código, logs, Firestore público ou arquivo `.env` versionado.
