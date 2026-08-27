# Promoções — integração com Nossa Gente

A tela **Promoções** usa o mesmo fluxo de autenticação do Nossa Gente. O funcionário informa CPF e senha, o NRD envia `POST /auth/login` e mantém o token somente na memória da sessão atual. A senha e o token não são persistidos no aparelho.

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

Enquanto a tela está aberta, o NRD faz uma verificação silenciosa a cada 60 segundos. Cada consulta de promoções usa identificador temporário e cabeçalhos anti-cache para solicitar uma resposta fresca. A resposta é normalizada e recebe uma assinatura SHA-256 estável, considerando produto, loja, período, preços, desconto, imagem e link, com ordenação independente da ordem recebida. Se a assinatura não mudou, a lista visível permanece intacta e o botão **Atualizar** fica neutro.

Quando a assinatura muda, o NRD mantém os produtos atuais na tela, guarda a nova resposta em memória e destaca o botão **Atualizar**. O funcionário pode tocar no botão para aplicar a nova lista; depois disso, o botão volta ao estado neutro. Se o funcionário tocar no botão estando neutro, o app apenas verifica a API e só destaca o botão caso encontre uma mudança.

O carregamento inicial continua exibindo progresso. A consulta automática não substitui a lista durante a navegação, e a preparação dos grupos ocorre fora da thread principal para evitar travamentos. Uma resposta HTTP 200 vazia ou incompatível não apaga silenciosamente uma lista válida: o app informa que o formato foi inesperado e permite tentar novamente. Um retorno HTTP 401/403 apaga a sessão local e solicita novo login.

## Ofertas Novas

O botão **Ofertas Novas** fica no cabeçalho do painel. Ele permanece neutro quando não há alterações registradas no dia e recebe destaque visual e contador quando o NRD detecta alterações. Ao abrir, o balão permite filtrar por loja e lista os produtos adicionados, alterados ou excluídos. Para mudanças de preço e validade, mostra o valor anterior e o atual; para exclusões, preserva a última informação conhecida.

O histórico não usa DataStore para guardar milhares de linhas. O NRD armazena localmente um snapshot técnico comprimido, limitado a 15.000 linhas, e até 5.000 eventos diários. A identidade da linha usa produto, loja e período de validade; preço não faz parte da identidade, evitando classificar uma simples redução de preço como produto novo. A primeira carga autenticada após a instalação da funcionalidade estabelece a linha de base e não marca todo o catálogo como novo. As próximas consultas, feitas enquanto o painel está aberto, calculam o delta fora da thread principal e não duplicam respostas idênticas.

O histórico contém apenas dados comerciais mínimos — código, nome, categoria, loja, preços, validade, imagem e link — e é apagado quando o funcionário toca em **Sair**. CPF, senha e token continuam sem persistência.

## Lojas, ordenação e sessão

Os códigos de filial são apresentados com nomes amigáveis por meio de `StoreCatalog`, mantendo o código entre parênteses para conferência. A tela permite escolher uma loja favorita no cabeçalho; a preferência é persistida no DataStore e passa a ser o filtro padrão na abertura do painel. O botão **Sair** limpa o token Nossa Gente da sessão atual e retorna ao login. Por decisão de compatibilidade, o token não é persistido; depois de fechar e reabrir o aplicativo, o funcionário precisa autenticar novamente.

Na lista de uma categoria, a chave **Ordenar** permite escolher nome, data de validade, ordem de adição, maior ou menor desconto e preço menor ou maior. A ordenação é feita localmente sobre os grupos já carregados, sem nova consulta à API.

O `PromotionNotificationWorker` e o consumidor FCM existentes não constituem uma notificação ponta a ponta para promoções: após a decisão de não persistir o token, o Worker em segundo plano não consegue autenticar com segurança quando o app está fechado. O novo botão **Ofertas Novas** funciona durante a sessão autenticada e com o painel aberto, aproveitando a verificação de 60 segundos já existente.

Para sincronização realmente instantânea ou alertas com o app fechado, o backend do Nossa Gente precisa publicar um evento/webhook ou alimentar um canal compartilhado de eventos com autorização própria. O APK não expõe webhook e o endpoint `/promocoes` exige token; por isso não é seguro colocar uma credencial administrativa no NRD nem persistir a senha/token de um funcionário.

## Configuração

A URL pode ser alterada por ambiente com:

```text
NOSSA_GENTE_API_BASE_URL=https://app.nordestao.com.br/nossa-gente/v1
```

Para homologação, use uma URL de homologação e um `applicationId`/flavor separado. Nunca coloque CPF, senha ou token em código, logs, Firestore público ou arquivo `.env` versionado.
