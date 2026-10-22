# Testes de permissões

Com Node 20/22 e Java 17, execute nesta pasta:

```sh
npm install
npm test
```

O emulador usa exclusivamente o projeto de demonstração `demo-nrd-access`; não altera contas ou dados de produção. Verifica acesso público, leitura do próprio cadastro, bloqueio de escalada de permissões, acesso do Mestre, validação dos documentos e revogação do acesso às escalas.
