# ProfileDAO 功能说明

## 1. 代码位置

- `src/dao/ProfileDAO.java`
- `src/model/Profile.java`
- `src/demo/ProfileDAODemo.java`

## 2. 这部分代码实现的功能

`ProfileDAO` 负责对助教个人资料进行基于 CSV 文件的本地持久化管理，数据文件为 `data/profiles.csv`。

它主要实现了以下功能：

- 自动检查并创建 `profiles.csv` 数据文件
- 从 `profiles.csv` 读取全部助教资料
- 按 `taId` 查询指定助教资料
- 当 `taId` 已存在时更新原有资料
- 当 `taId` 不存在时新增一条资料记录
- 将更新后的全部资料重新写回 CSV 文件

## 3. 数据字段说明

每条资料记录包含 5 个字段，顺序如下：

1. `taId`：助教编号
2. `name`：姓名
3. `studentId`：学号
4. `major`：专业
5. `phone`：手机号

CSV 示例：

```text
T001,Alice,20231234,ComputerScience,13800138000
```

## 4. 核心方法说明

- `getAllProfiles()`：读取并返回全部助教资料
- `getByTaId(String taId)`：根据助教编号查询资料
- `saveOrUpdate(Profile profile)`：新增或更新一条助教资料

## 5. 执行演示命令

在项目目录 `2` 下执行：

```bash
javac -d out src/model/Profile.java src/dao/ProfileDAO.java src/demo/ProfileDAODemo.java
java -cp out demo.ProfileDAODemo
```

说明：

- 第一条命令用于编译 `Profile`、`ProfileDAO` 和演示类
- 第二条命令用于运行演示程序
- 演示程序会临时写入一条示例数据，再把原始 `profiles.csv` 内容恢复，不会影响你现有数据

## 6. 示例输出

```text
=== ProfileDAO Demo ===
[1] Existing profile count: 1
[2] Saved profile: T002,Bob,20239876,SoftwareEngineering,13900139000
[3] Query by TA ID (T002): T002,Bob,20239876,SoftwareEngineering,13900139000
[4] Profile count after saveOrUpdate: 2
```

## 7. 功能展示说明

从以上输出可以看出，这部分代码已经完成了：

- 读取原有助教资料
- 新增一条新的助教资料
- 通过 `taId` 成功查询到刚写入的数据
- 再次读取全部资料时，记录总数发生变化，说明写入成功

这说明 `ProfileDAO` 已经具备基础的资料持久化、查询和更新能力，可作为助教信息管理模块的数据访问层使用。
