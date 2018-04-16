# 类库
## 引用
	compile ("org.brewchain:org.brewchain.frontend:{version}")
## 注入
	@ActorRequire(name = "Account_Helper", scope = "global")
	AccountHelper accountHelper;

	@ActorRequire(name = "Transaction_Helper", scope = "global")
	TransactionHelper transactionHelper;

	@ActorRequire(name = "Block_Helper", scope = "global")
	BlockHelper blockHelper;
# 账户API
## 创建账户
	act/pbcac.do
## 获取账户信息
	act/pbgac.do

# 交易API
## 创建交易
	txt/pbstx.do
## 创建多重交易
	txt/pbmtx.do
## 获取未广播交易
	txt/pbgut.do
## 广播交易
	txt/pbayc.do
## 获取交易
	txt/pbgtx.do

# 区块API
## 生成区块
	btc/pbgbc.do
## 广播区块
	btc/pbsbc.do