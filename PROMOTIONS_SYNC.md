# Promoções — integração com Nossa Gente

A tela **Promoções** usa o mesmo fluxo de autenticação do Nossa Gente. O funcionário informa CPF e senha e o NRD envia `POST /auth/login`. CPF e senha nunca são gravados no aparelho. Após o login, somente o token retornado pela API é persistido de forma cifrada com uma chave AES mantida pelo Android Keystore, permitindo restaurar a sessão depois que o aplicativo é fechado ou o aparelho é reiniciado.

O botão **Sair** apaga o token persistido. Se a API responder HTTP 401/403, o NRD considera a sessão expirada, remove o token local e solicita novo login.

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

## Atualização com a tela aberta

Enquanto a tela está aberta, o NRD faz uma verificação silenciosa a cada 60 segundos. Cada consulta de promoções usa identificador temporário e cabeçalhos anti-cache para solicitar uma resposta fresca. A resposta é normalizada e recebe uma assinatura SHA-256 estável, considerando produto, loja, período, preços, desconto, imagem e link, com ordenação independente da ordem recebida. Se a assinatura não mudou, a lista visível permanece intacta e o botão **Atualizar** fica neutro.

Quando a assinatura muda, o NRD mantém os produtos atuais na tela, guarda a nova resposta em memória e destaca o botão **Atualizar**. O funcionário pode tocar no botão para aplicar a nova lista; depois disso, o botão volta ao estado neutro. Se o funcionário tocar no botão estando neutro, o app apenas verifica a API e só destaca o botão caso encontre uma mudança.

O carregamento inicial continua exibindo progresso. A consulta automática não substitui a lista durante a navegação, e a preparação dos grupos ocorre fora da thread principal para evitar travamentos. Uma resposta HTTP 200 vazia ou incompatível não apaga silenciosamente uma lista válida: o app informa que o formato foi inesperado e permite tentar novamente.

## Ofertas Novas

O botão **Ofertas Novas** fica no cabeçalho do painel. Ele permanece neutro quando não há alterações registradas no dia e recebe destaque visual e contador quando o NRD detecta alterações. Ao abrir, o balão permite filtrar por loja e lista os produtos adicionados, alterados ou excluídos. Para mudanças de preço e validade, mostra o valor anterior e o atual; para exclusões, preserva a última informação conhecida.

O histórico não usa DataStore para guardar milhares de linhas. O NRD armazena localmente um snapshot técnico comprimido, limitado a 15.000 linhas, e até 5.000 eventos diários. A identidade da linha usa produto, loja e período de validade; preço não faz parte da identidade, evitando classificar uma simples redução de preço como produto novo. A primeira carga autenticada após a instalação da funcionalidade estabelece a linha de base e não marca todo o catálogo como novo. As próximas consultas, feitas enquanto o painel está aberto, calculam o delta fora da thread principal e não duplicam respostas idênticas.

O histórico contém apenas dados comerciais mínimos — código, nome, categoria, loja, preços, validade, imagem e link. CPF e senha não são persistidos.

## Lojas, ordenação e sessão

Os códigos de filial são apresentados com nomes amigáveis por meio de `StoreCatalog`, mantendo o código entre parênteses para conferência. A tela permite escolher uma loja favorita no cabeçalho; a preferência é persistida no DataStore e passa a ser o filtro padrão na abertura do painel.

A sessão do Nossa Gente permanece ativa enquanto o token for aceito pela API, mesmo depois que o usuário fecha o NRD Lojas ou reinicia o aparelho. O token fica cifrado localmente pelo Android Keystore. O botão **Sair** remove imediatamente esse token e retorna ao login.

Na lista de uma categoria, a chave **Ordenar** permite escolher nome, data de validade, ordem de adição, maior ou menor desconto e preço menor ou maior. A ordenação é feita localmente sobre os grupos já carregados, sem nova consulta à API.

## Notificações da loja favorita

O `PromotionNotificationWorker` verifica a loja favorita em segundo plano com WorkManager, respeitando o intervalo periódico mínimo de 15 minutos e exigindo conexão com a internet. Como o Worker consegue restaurar o token cifrado, a consulta pode funcionar mesmo quando o aplicativo não está aberto.

A primeira verificação estabelece uma linha de base e não dispara uma notificação com todo o catálogo. Nas verificações seguintes, o Worker compara os códigos dos produtos atuais da loja favorita com o snapshot anterior e conta somente produtos que realmente apareceram desde a última verificação. Mudanças apenas de preço, validade ou ordenação não entram nessa contagem de novos produtos.

Exemplos de mensagem:

```text
Nova promoção em <Loja>
Foi adicionado 1 produto à sua loja favorita.
```

```text
Novas promoções em <Loja>
Foram adicionados 7 produtos à sua loja favorita.
```

Ao tocar na notificação, o NRD abre diretamente a aba **Promoções**. Se a sessão ainda estiver válida, o usuário entra no painel; se o token tiver expirado, o fluxo retorna ao login.

O WorkManager não garante entrega instantânea em segundos. Para alertas realmente em tempo real, o backend do Nossa Gente ainda precisaria publicar um evento/webhook ou alimentar um canal FCM com autorização própria. Não deve ser colocada credencial administrativa, CPF ou senha de funcionário dentro do APK para contornar essa limitação.

## Configuração

A URL pode ser alterada por ambiente com:

```text
NOSSA_GENTE_API_BASE_URL=https://app.nordestao.com.br/nossa-gente/v1
```

Para homologação, use uma URL de homologação e um `applicationId`/flavor separado. Nunca coloque CPF, senha ou token em código, logs, Firestore público ou arquivo `.env` versionado.
