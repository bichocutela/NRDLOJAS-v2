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

A tela interpreta os nomes mais comuns para o retorno de promoções e produtos em oferta, incluindo `data`, `promocoes`, `promotions`, `items`, `produtos`, `products`, `itens` e `ofertas`. O contrato definitivo deve ser confirmado com a API.

## Atualização

Enquanto a tela está aberta, o NRD consulta novamente a API a cada 60 segundos e também oferece o botão **Atualizar**. Um retorno HTTP 401/403 apaga a sessão local e solicita novo login.

Essa atualização é um fallback controlado. Para sincronização realmente instantânea quando uma promoção ou produto em oferta mudar, o backend do Nossa Gente precisa publicar um evento/webhook ou alimentar um canal compartilhado de eventos. O APK não expõe webhook e o endpoint `/promocoes` exige token; por isso não é seguro colocar uma credencial administrativa no NRD nem depender do token de outro funcionário.

## Configuração

A URL pode ser alterada por ambiente com:

```text
NOSSA_GENTE_API_BASE_URL=https://app.nordestao.com.br/nossa-gente/v1
```

Para homologação, use uma URL de homologação e um `applicationId`/flavor separado. Nunca coloque CPF, senha ou token em código, logs, Firestore público ou arquivo `.env` versionado.
