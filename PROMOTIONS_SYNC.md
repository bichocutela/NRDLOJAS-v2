# Sincronização de Promoções

A documentação atual está em [docs/PROMOTIONS_ARCHITECTURE.md](docs/PROMOTIONS_ARCHITECTURE.md).

Promoções usa o gateway autorizado do NRD para consultar ACP e os documentos de ofertas publicados pelo Mestre. Room persiste as ofertas; Flow e ViewModel alimentam a interface. A abertura usa os dados locais, e apenas alterações são gravadas durante a sincronização.

O botão Atualizar e o gesto de puxar executam a mesma varredura manual. O monitor verifica automaticamente a cada 60 segundos em primeiro plano, inclusive em outras abas; WorkManager faz a verificação periódica em segundo plano conforme permitido pelo Android.

O acesso a Promoções segue as permissões liberadas pelo Mestre. A aba não exige login Nossa Gente para consultar ofertas.
