# Revisão do contrato de autenticação

## Conclusão

O APK Nossa Gente auditado contém, no bundle, uma função `login` que monta `POST /auth/login` com o payload `{ cpf, senha }`. Esse artefato, entretanto, não representa o fluxo operacional informado para a versão usada pela equipe: o acesso real é feito por **matrícula**.

O NRD Lojas v2 deve seguir a regra operacional atual e enviar:

```json
{
  "matricula": "<matrícula informada pelo funcionário>",
  "senha": "<senha informada pelo funcionário>"
}
```

A matrícula deve ser tratada como texto, preservando zeros à esquerda, e a senha deve ser usada somente na requisição. O token recebido é a única informação persistida localmente, cifrada com Android Keystore.

## Limite da validação

Não foi usada nenhuma credencial real nesta auditoria. A confirmação definitiva do nome do campo no backend exige uma conta de homologação autorizada ou documentação da API. A implementação foi corrigida para `matricula` conforme o fluxo informado pelo responsável do sistema, e o build deve ser testado com uma matrícula real no aparelho.
