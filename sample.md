# 查询账户
    http://ta201.icwv.co:38001/fbs/act/pbgas.do
## 请求
    {
        "address": ["40d9e05090cde533d59f09441f441b15c4862e4b","feeffde4da783632816d17047f4d2e3b279d0777"]
    }
    地址可以是一个或者多个

# 查询交易
    http://ta201.icwv.co:38001/fbs/txt/pbgtx.do
## 请求
    {
        "hexTxHash": "2f88edcdede7e0ce9f7ffa1819f5cfd74913a0c6e35150ec1f7bd3aaded0a218"
    }

# CWB交易
    http://ta201.icwv.co:38001/fbs/tst/pbstt.do
## 请求
    {
        "input": [{
            "address": "40d9e05090cde533d59f09441f441b15c4862e4b",
            "amount": "-1",
            "prikey": "c24e61d8010d135693144f3873c5ee504f2f72f54e624b8b98b7109d11e56e46",
            "putkey": "58506e0ddad1ba83b1e3bfc10fb471254d3113ac2e1c7cc5b257747118e5688780f7c83b8034b1beef784ae803f318354daae945046e56da8030460316db225b"
        }],
        "output": [{
        "address": "feeffde4da783632816d17047f4d2e3b279d0777",
        "amount": "-1"
        }],
        "data": ""
    }

# 加密token交易
    http://ta201.icwv.co:38001/fbs/tst/pbtro.do
## 请求
    {
        "input": [{
            "address": "feeffde4da783632816d17047f4d2e3b279d0777",
            "erc721token": "d84017f61b74993dd04d5363e6e9f056656f13ab921aa6b580c2aa303cf3b236",
            "erc721symbol": "house",
            "amount": "-9"
        }],
        "output": [{
            "address": "3d7d44447c4a79f89a7153c93ea314663e3f81c0",
            "amount": "-9",
            "erc721token": "d84017f61b74993dd04d5363e6e9f056656f13ab921aa6b580c2aa303cf3b276",
            "erc721symbol": "house"
        }],
        "signature": [{"privKey": "8fb394a9b71bed2f0582878ab67ceba7f922100448165202c1449483cf886339"},{"privKey":""}],
        "data": ""
    }

# 创建合约
    http://ta201.icwv.co:38001/fbs/tst/pbtcc.do
## 请求
    {
        "address": "c34b0a15a01bb4f583e52393f75de9c28ed757fa",
        "pubKey": "4a85b6ad5b1385470a152b694e0c2a38666d1f633001f5a565f1887709fe7ab8101515bcc8a49e88e89c98fd91060a56a44fd4556dad36b8f9f6e1dfd663c945",
        "privKey": "0be9cb10656dc173b8456321fa17550a68af339b0ddd7e560796111d8f008b0a",
        "data": "608060405260008055348015601357600080fd5b5060a2806100226000396000f300608060405260043610603f576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff16806367e0badb146044575b600080fd5b604a6060565b6040518082815260200191505060405180910390f35b60006001600054016000819055506000549050905600a165627a7a7230582062fd3e962a4692878af614957db732bc3517dcb6324935eeb68878c0f30cb84b0029"
    }
    
# 创建token
	http://localhost:8000/fbs/tst/pbtct.do
## 请求
	{
		 "input": [{
			"address": "feeffde4da783632816d17047f4d2e3b279d0777",
			"erc20symbol": "XYZ",
			"amount": "100000"
		 }],
		 "signature": [{"privKey": "8fb394a9b71bed2f0582878ab67ceba7f922100448165202c1449483cf886339"}],
		 "data": ""
	}

# 交易token
	http://localhost:8000/fbs/txt/pbtoo.do
## 请求
	{
		 "input": [{
			"address": "40d9e05090cde533d59f09441f441b15c4862e4b",
			"erc20symbol": "XYZ",
			"amount": "100000"
		 }],
		 "output": [{
			"address": "c11e6aafb44f58ca495da874a5f55df5b8d5ee34",
			"amount": "100000"
		 }],
		 "signature": [{"privKey": "c24e61d8010d135693144f3873c5ee504f2f72f54e624b8b98b7109d11e56e46"}],
		 "data": ""
	}
	

# 调用合约
    http://ta201.icwv.co:38001/fbs/tst/pbtec.do
## 请求
    {
        "address": "12f110baba62a4d03736cd9c17b39a5874a2408f",
        "pubKey": "68d62d938220998afc4c0c0300266ae728c27a9f1c90aa45462b32e38dc8956588ff81c7c1b1eaf21f4c423172ea75dbc6a6849b132122d336cc27ce543f212f",
        "privKey": "21706acb22d4692bb86d478c634d14a68636165e0a713de833eb67ef5e648983",
        "contract": "4508684405249762081ae8fdadc963d01de8904d08cbb85adc4216c6049aedba",
        "data": "67e0badb"
    }

# 测试账户
## CWB
    地址    
    7e318319493d7aa4254653fd7d082a4a25c4d2f3
    私钥    
    d68323fefd4f26baf6dbbb6dc5ce4f19e3b502ba73e630564158b60a46831ec0
    公钥
    a393a5a77e0b80409b4937f48ab5ce0ab011a0fd61fb17b520d030b36cdf4708918abcfc3101609700da4f056f77ce75e58280e086de17e990bc2f1aa516f5a3
------
    地址
    a1d1b2fa96fbb6473c4ce596133bb2d5a70bc834
    私钥
    de5a5afc8b246b129ce2fa52c654c66a62bb297ac8eaa20d943de3ac4676ae87
    公钥
    abfd0e2fcffbf4e864454c98435e5fd31fc61608bb3d3322b371bcbfa01be5c715e3a0f880c21655522334f22d341f8bc616e87732ee9ef0a43cba9cc8ca2652
------
    地址
    c465bfc8f63373b191070e7993b00323e7b65af0
    私钥
    c03d05b1bd99753855de1e0741db709bf7582baefc91fa4fa43e613bb759b78c
    公钥
    56112232faa2b5b22df68cda7f870f867722e8f35aa199f9bb5870b718d30bcc1ee667d0d28db40e98dfa738af2983c2eb5a346b6ca88ed6bcd5e95ca2daedf3

## CWS
    地址
    1e1051efe9ff0040c9d88e2fb024f1a6a70ad43c
    私钥
    302c6797c1f95bed266c1ceae86878f1468bbf133fb1e2a70001efbc89321762
    公钥
    4a487718a251a72e157570de5d6bb1918338b02e5805a1204972f53a1a1fa43e0b04a03ccad43481134ca4a9b60e46b9f05d4bbeb39a4424d987984de5fb0ddf
------
    地址
    3ba64db752e3e49a816ca6aad61c28a05e66b18c
    私钥
    fd87751fc79a20a02bf56d538afc55411532a74c60f11560cc926c9681ebd55d
    公钥
    3d1b8ca136013f251521e01365f5ee83e45c44b6d06dbb473194da4ba0a1c71e8d8b7a46bd54aca10110c3f33000cca447e3701fd916c6af31a9245a687ed03f
------
    地址
    c625a56426bda6f8f64c85983ebe619f97aa44e5
    私钥
    85a5f877142b707c375a6223450a8e4c7414a001cc3b6bd2eb9964dbcd18896d
    公钥
    d4685adf1d566304483d79ec55bae8e80a1cba83c436fee6b0d555066339275ed9f769a7e6fea3d5574e385a4a5bddae0d62fbd0e7edc0f73a9f817105f87b87

## 加密token
    地址
    feeffde4da783632816d17047f4d2e3b279d0777
    私钥
    8fb394a9b71bed2f0582878ab67ceba7f922100448165202c1449483cf886339
    公钥
    84b89e5219e57cb172e9bed849ac38857957eb421bdff1ec3cd4f3a29a371076b31ccc6205c513c99c62a5c7c3840f1ffc771b9d7485985e424d481c4f2cf203