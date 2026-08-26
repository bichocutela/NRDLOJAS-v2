# Revisão do contrato de autenticação

## Conclusão

O APK Nossa Gente auditado contém, no bundle, uma função `login` que monta `POST /auth/login` com o payload `{ cpf, senha }`. Esse artefato, entretanto, não representa o fluxo operacional informado para a versão usada pela equipe: o acesso real é feito por **matrícula**.

O NRD Lojas v2 deve seguir a regra operacional atual e enviar a matrícula como valor do identificador. A API REST legada, porém, espera a chave técnica `cpf`:

```json
{
  "cpf": "<matrícula informada pelo funcionário>",
  "senha": "<senha informada pelo funcionário>"
}
```

A matrícula deve ser tratada como texto, preservando zeros à esquerda, e a senha deve ser usada somente na requisição. O token recebido é a única informação persistida localmente, cifrada com Android Keystore.

Os testes comparativos confirmaram que `{ "matricula": ..., "senha": ... }` retorna `dados_invalidos`, enquanto `{ "cpf": ..., "senha": ... }` passa da validação estrutural e chega à validação de credenciais. O segundo retorno foi `credenciais_invalidas` para a conta de teste fornecida, sem autenticação bem-sucedida.

## Limite da validação

Não foi usada nenhuma credencial real nesta auditoria. A confirmação definitiva do nome do campo no backend exige uma conta de homologação autorizada ou documentação da API. A implementação foi corrigida para exibir matrícula e enviar o valor na chave técnica `cpf`, conforme o contrato efetivo observado. O build deve ser testado com uma matrícula e senha válidas no aparelho.
