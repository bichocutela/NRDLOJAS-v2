# Revisão do contrato de autenticação

## Conclusão

O APK Nossa Gente auditado contém, no bundle, uma função `login` que monta `POST /auth/login` com o payload `{ cpf, senha }`. A página oficial antiga pode exibir o texto “Matrícula” em alguns campos, mas o contrato REST efetivo confirmado para a conta de teste usa **CPF**.

O NRD Lojas v2 deve solicitar CPF ao funcionário e enviar:

```json
{
  "cpf": "<CPF informado pelo funcionário>",
  "senha": "<senha informada pelo funcionário>"
}
```

O CPF é normalizado para os 11 dígitos, aceitando entrada com ou sem pontuação, e a senha é usada somente na requisição. O token recebido é a única informação persistida localmente, cifrada com Android Keystore.

Os testes comparativos confirmaram que o payload com chave `matricula` retorna `dados_invalidos`. Com CPF na chave `cpf`, a autenticação de teste retornou HTTP 200 e a consulta autenticada de promoções retornou HTTP 200.

## Limite da validação

A credencial de exemplo fornecida pelo responsável foi usada uma única vez para validar o contrato, sem salvar CPF, senha ou token. A implementação foi corrigida para exibir CPF e enviar o valor na chave `cpf`, conforme o contrato efetivo confirmado. O login de teste retornou HTTP 200 e a consulta de promoções retornou HTTP 200; ainda assim, o build deve ser testado no aparelho com um CPF autorizado.
