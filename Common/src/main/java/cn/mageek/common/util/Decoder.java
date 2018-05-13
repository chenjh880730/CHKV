package cn.mageek.common.util;

import cn.mageek.common.model.DataRequest;
import cn.mageek.common.model.DataResponse;
import cn.mageek.common.model.HeartbeatRequest;
import cn.mageek.common.model.HeartbeatResponse;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.EncoderException;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.LinkedList;
import java.util.List;
import static cn.mageek.common.model.LineType.*;
import static cn.mageek.common.res.Constants.*;

/**
 * 入站数据解码
 * @author Mageek Chiu
 * @date 2018/5/6 0007:16:20
 */
public class Decoder {

    private static final int MINIMAL_LENGTH = 6;
    private static final Logger logger = LoggerFactory.getLogger(Decoder.class);

    /**
     * 将接收到的bit数据解析为消息对象DataRequest的列表，兼容几个redis命令的协议
     * @param in 输入buffer
     * @return DataRequest
     */
    public static List<DataRequest> bytesToDataRequests(ByteBuf in) throws Exception{
        String msg = in.toString(CharsetUtil.UTF_8);
        if (in.readableBytes()<MINIMAL_LENGTH){
            throw new EncoderException("less than MINIMAL_LENGTH  "+msg);
        }
        return getDataRequests(msg);
    }

    // 简化的解码方式：要么7行(set)，要么5行(get、del)，要么3行(COMMAND 也就是redis-cli 连接时发送的命令)
    private static List<DataRequest> getDataRequests(String s) throws Exception {

        List<DataRequest> dataRequestList = new LinkedList<>();

        String[] msgs = s.split(outerSplit);// 多个命令之间用 \t\n 分割  // 这个不属于redis，是我为了自己方便加的
        logger.debug("Decoder 获得 {} 条命令",msgs.length);
        for (String msg : msgs) {
            String[] strings = msg.split(innerSplit);// 单个命令内部用 \r\n 分割

            // 简单的校验
            int allLineNumber = strings.length;
            String ID = strings[allLineNumber-1];// Client发过来的才是ID,redis 协议没有这一段，所以可能是任何字段
            int ckvLineNumber = Integer.parseInt(strings[0].substring(1));
//            if (allLineNumber != 7 && allLineNumber != 5  && allLineNumber != 3) throw new Exception("all line number Exception");// 报文总行数
//            if (ckvLineNumber != 3 && ckvLineNumber != 2 && ckvLineNumber != 1) throw new Exception("command、key、value line number Exception");// command、key、value 的行数

            String command = strings[2].toUpperCase();// 命令全部转大写
            if (Integer.parseInt(strings[1].substring(1)) != command.length()) throw new Exception("command length Exception");
            if (command.equals("COMMAND")){// 没有 key
                dataRequestList.add(new DataRequest(command,"none","none",ID));
                continue;
            }

            // 有 key
            String key = strings[4];
            if (Integer.parseInt(strings[3].substring(1)) != key.length()) throw new Exception("key length Exception");

            String value = "none";// 没有 value
            if (allLineNumber >= 7){// 有 value
                value = strings[6];
                if (Integer.parseInt(strings[5].substring(1)) != value.length()) throw new Exception("value length Exception");
            }
            dataRequestList.add(new DataRequest(command,key,value,ID));
        }
        return dataRequestList;
    }

    /**
     * 将接收到的bit数据解析为消息对象DataResponse的列表，是redis协议的子集
     * @param in 输入buffer
     * @return DataRequest
     */
    public static DataResponse bytesToDataResponse(ByteBuf in) throws Exception{

        String data = in.toString(CharsetUtil.UTF_8);
        String[] datas = data.split(outerSplit);
        String ID = "";
        if (datas.length>1){// 是client发出的请求
//            logger.debug("Client:{},{},{}",data,datas[0],datas[1]);
            data = datas[0];
            ID = datas[1];
        }
        String lineType = data.substring(0,1);
        String msg = "";
        switch (lineType){
            case SINGLE_RIGHT:
            case SINGLE_ERROR:
            case INT_NUM:
                msg = data.substring(1,data.length()-innerSplit.length());
                break;
            case NEXT_LEN:
                if( data.length()==(3+innerSplit.length())) msg="-1";// $-1\r\n
                else msg = data.split(innerSplit)[1];// $3\r\nGGG\r\n
                break;
            case LINE_NUM:
                break;
        }
        DataResponse response = new DataResponse(lineType,msg,ID);
        return response;
    }

    /**
     * 将接收到的bit数据解析为消息对象HeartbeatRequest
     * @param in 输入buffer
     * @return DataRequest
     */
    public static HeartbeatRequest bytesToHeartbeatRequest(ByteBuf in) throws Exception{
        return new HeartbeatRequest("","", 10000000L);
    }
    /**
     * 将接收到的bit数据解析为消息对象HeartbeatResponse
     * @param in 输入buffer
     * @return DataRequest
     */
    public static HeartbeatResponse bytesToHeartbeatResponse(ByteBuf in) throws Exception{
        return new HeartbeatResponse(true,5,null);
    }

}
