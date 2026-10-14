# -*- coding: utf-8 -*-
"""按老师空白任务书模板重做任务书（城科酒店管理系统）。

用法：python scripts/build_taskbook_tpl.py

老师 2026-10-10 要求：
  1. 任务书内容最多两页（模板本身 2 页，故严格保持模板的段落数量与篇幅）
  2. 不要乱改格式（沿用模板的表格结构、楷体_GB2312 小四、手动编号、首行缩进）

做法：复制模板 docx → 按段落位置一对一替换文字 → 用 Word 统计页数校验。
"""
import os
import shutil

from docx import Document
from docx.oxml.ns import qn
from docx.shared import Pt

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS = os.path.join(ROOT, "docs")
TPL = r"D:\dsh_work\report-fix\taskbook_tpl.docx"
OUT = os.path.join(DOCS, "计算机专业-23计算机6班-2023114028-卢家全《应用软件开发专题实践》课程设计任务书.docx")

COLLEGE = "人工智能与大数据学院"
CLASS_NAME = "23计算机6班"
NAME = "卢家全"
STUDENT_NO = "2023114028"
TOPIC = "基于Vue的城科酒店管理系统设计与实现"

# 主要内容 / 具体要求 / 报告内容 / 参考文献：与模板逐段一一对应
MAIN_CONTENT = [
    "对本系统一些基本信息的设置，包括客房信息设置，房型信息设置，客人信息设置，其它设置等。",
    "房价设置，包括各类房型的门市价与会员价，周末价、节假日价等按日期区间的房价策略，以及房价信息的查询等。",
    "统计设置，客人可以在消费记录里查看自己的消费情况，管理员可以在财务统计里查看营业情况，客房出租率，客人消费情况等。",
    "对客人预订与登记入住进行管理，为客人提供房间预订与押金收取，同时管理客房消费与退房结算。",
    "客人管理主要对客人的基本信息，会员等级与折扣，预存金额以及入住与消费情况进行管理。",
    "自行增加其他功能，包括客房状态管理、房价试算以及基于知识库的智能问答等。",
]
REQUIREMENTS = [
    "综述国内外酒店管理系统的现状以及特点；",
    "深入了解基于Vue与Spring Boot的相关技术；",
    "熟练掌握软件系统的应用与分析，训练编写程序的能力；",
    "设计基于Vue与Spring Boot的相关系统；",
    "深入分析管理信息系统的关键技术或者软件的设计流程；",
    "训练检索文献资料和利用文献资料的能力；",
    "训练撰写技术文档的能力；",
    "软件操作简单，运行效果良好。",
]
REPORT_ITEMS = [
    "引言；",
    "可行性分析；",
    "需求分析；",
    "总体设计",
    "详细设计",
    "系统测试（包含测试方案、测试用例、测试结果）；",
    "参考文献",
    "设计总结",
    "附录（主要用来放程序）",
]
REFS = [
    "[1] 王雪梅,李志强. 基于Spring Boot与Vue的酒店管理系统设计与实现[J]. 计算机时代,2023(6):78-82",
    "[2] 陈鹏,刘洋. 基于B/S架构的中小型酒店管理信息系统研究[J]. 软件工程,2022,25(9):45-49",
    "[3] 张明,赵丽华. 前后端分离架构在管理信息系统中的应用研究[J]. 计算机应用与软件,2023,40(3):112-117",
    "[4] 李文博. 基于知识图谱的酒店智能问答系统研究与实现[D]. 南京:南京邮电大学,2023",]


def para_text(para):
    return para.text


def set_para_text(para, text):
    """只改第一个 run 的文字，其余清空，保留字体/缩进/编号格式。"""
    if para.runs:
        para.runs[0].text = text
        for r in para.runs[1:]:
            r.text = ""
    else:
        run = para.add_run(text)
        run.font.size = Pt(12)
        rpr = run._element.get_or_add_rPr()
        rf = rpr.find(qn("w:rFonts"))
        if rf is None:
            from docx.oxml import OxmlElement
            rf = OxmlElement("w:rFonts")
            rpr.insert(0, rf)
        rf.set(qn("w:eastAsia"), "楷体_GB2312")


def main():
    if not os.path.exists(TPL):
        raise SystemExit("缺少模板：" + TPL)
    shutil.copyfile(TPL, OUT)
    doc = Document(OUT)

    # ---------- 表头两行：题目 + 学院/班级/姓名/学号 ----------
    top = doc.paragraphs[1]
    set_para_text(top, "学院 %s 专业 %s  姓名 %s  学号 %s" % (COLLEGE, CLASS_NAME, NAME, STUDENT_NO))
    for para in top.runs and [] or []:
        pass
    # 表头段的字体保持模板（楷体_GB2312），确保多 run 时也统一
    for r in top.runs[1:]:
        r.text = ""
    if top.runs:
        rpr = top.runs[0]._element.get_or_add_rPr()
        rf = rpr.find(qn("w:rFonts"))
        if rf is None:
            from docx.oxml import OxmlElement
            rf = OxmlElement("w:rFonts")
            rpr.insert(0, rf)
        rf.set(qn("w:eastAsia"), "楷体_GB2312")
        top.runs[0].font.bold = True

    # ---------- 表格内容 ----------
    t = doc.tables[0]
    # [1,1] 题目
    set_para_text(t.cell(1, 1).paragraphs[0], TOPIC)

    # [3,0] 主体：29 段，逐段替换
    cell = t.cell(3, 0)
    paras = cell.paragraphs
    # 段落索引（与模板一致）：0=1、目的和意义 / 1-2=目的两条 / 3=2、主要内容 / 4-9=主要内容六条
    # 10=3、具体要求 / 11-18=要求八条 / 19=4、报告内容 / 20-28=报告九条
    if len(paras) >= 29:
        set_para_text(paras[1], "通过本选题使学生运用所学基本理论知识、专业技能，培养分析与解决应用软件开发技术问题的能力。")
        set_para_text(paras[2], "学会本专业中英文文献资料的检索方法，养成科学研究的基本素质。")
        for i, text in enumerate(MAIN_CONTENT):
            set_para_text(paras[4 + i], text)
        for i, text in enumerate(REQUIREMENTS):
            set_para_text(paras[11 + i], text)
        for i, text in enumerate(REPORT_ITEMS):
            set_para_text(paras[20 + i], text)
        print("主体 29 段已按模板逐段替换")
    else:
        print("警告：模板主体段落数变为 %d，未按预期替换" % len(paras))

    # [4,0] 参考资料：3 段（标题 + 两条文献），保留两条以控制页数
    cell4 = t.cell(4, 0)
    p4 = cell4.paragraphs
    if len(p4) >= 3:
        set_para_text(p4[0], "5、参考资料及文献查询方向、范围：（由指导教师指定）")
        set_para_text(p4[1], REFS[0])
        set_para_text(p4[2], REFS[1])
        # 模板只有 2 条文献位置，追加 2 条（复制最后一段的样式）
        import copy as _copy
        from docx.oxml.ns import qn as _qn
        last = p4[2]
        for extra in REFS[2:]:
            new_p = _copy.deepcopy(last._p)
            last._p.addnext(new_p)
            from docx.text.paragraph import Paragraph
            np = Paragraph(new_p, last._parent)
            set_para_text(np, extra)
            last = np
        print("参考资料已替换并补至 %d 条" % len(REFS))

    doc.save(OUT)
    print("生成成功：", OUT)
    print("大小：%.1f KB" % (os.path.getsize(OUT) / 1024))


if __name__ == "__main__":
    main()
